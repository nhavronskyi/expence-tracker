package pl.havronskyi.finance.review;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.havronskyi.finance.domain.*;
import pl.havronskyi.finance.repo.MerchantRuleRepository;
import pl.havronskyi.finance.repo.ReviewItemRepository;
import pl.havronskyi.finance.repo.TxnRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class ReviewService {

    private final ReviewItemRepository reviews;
    private final TxnRepository txns;
    private final MerchantRuleRepository rules;

    public ReviewService(ReviewItemRepository reviews, TxnRepository txns, MerchantRuleRepository rules) {
        this.reviews = reviews;
        this.txns = txns;
        this.rules = rules;
    }

    public List<ReviewCard> open() {
        return reviews.findByStatusOrderByIdAsc(ReviewStatus.OPEN).stream()
                .map(r -> new ReviewCard(r.getId(), r.getTxnId(), r.getQuestion(), r.getSuggestions()))
                .toList();
    }

    /**
     * The heart of the project: the user's answer doesn't end with one transaction,
     * it turns into a rule. Otherwise the queue would never shrink.
     */
    @Transactional
    public void resolve(Long reviewId, ResolveRequest req) {
        ReviewItem item = reviews.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Brak pozycji review " + reviewId));
        Txn txn = txns.findById(item.getTxnId())
                .orElseThrow(() -> new IllegalArgumentException("Brak transakcji " + item.getTxnId()));

        txn.setCategory(req.category());
        txn.setKind(req.kind() == null ? txn.getKind() : req.kind());
        txn.setCategorySource(CategorySource.MANUAL);
        txn.setConfidence(BigDecimal.ONE);
        txn.setNeedsReview(false);
        txns.save(txn);

        if (req.learnRule() && txn.getMerchantNorm() != null && !txn.getMerchantNorm().isBlank()) {
            rules.findByMatchTypeAndPattern(MatchType.EXACT, txn.getMerchantNorm())
                    .ifPresentOrElse(
                            existing -> {
                                existing.setCategory(req.category());
                                existing.setKind(txn.getKind());
                                rules.save(existing);
                            },
                            () -> {
                                MerchantRule rule = new MerchantRule();
                                rule.setMatchType(MatchType.EXACT);
                                rule.setPattern(txn.getMerchantNorm());
                                rule.setCategory(req.category());
                                rule.setKind(txn.getKind());
                                rules.save(rule);
                            });
        }

        item.setStatus(ReviewStatus.RESOLVED);
        item.setResolvedAt(Instant.now());
        reviews.save(item);
    }
}
