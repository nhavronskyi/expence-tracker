package pl.havronskyi.finance.review;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.havronskyi.finance.domain.*;
import pl.havronskyi.finance.repo.CategoryRepository;
import pl.havronskyi.finance.repo.MerchantRuleRepository;
import pl.havronskyi.finance.repo.ReviewItemRepository;
import pl.havronskyi.finance.repo.TxnRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewItemRepository reviews;
    private final TxnRepository txns;
    private final MerchantRuleRepository rules;
    private final CategoryRepository categories;

    public ReviewService(ReviewItemRepository reviews, TxnRepository txns, MerchantRuleRepository rules,
                         CategoryRepository categories) {
        this.reviews = reviews;
        this.txns = txns;
        this.rules = rules;
        this.categories = categories;
    }

    public List<ReviewCard> open() {
        List<ReviewItem> items = reviews.findByStatusOrderByIdAsc(ReviewStatus.OPEN);
        Map<Long, Txn> txnById = txns.findAllById(items.stream().map(ReviewItem::getTxnId).toList()).stream()
                .collect(Collectors.toMap(Txn::getId, t -> t));

        return items.stream()
                .map(r -> {
                    Txn t = txnById.get(r.getTxnId());
                    String merchant = t.getMerchantNorm() == null || t.getMerchantNorm().isBlank()
                            ? t.getDescription() : t.getMerchantNorm();
                    return new ReviewCard(
                            r.getId(),
                            r.getTxnId(),
                            merchant,
                            t.getDescription(),
                            new BigDecimal(t.getAmountMinor()).movePointLeft(2),
                            t.getCurrency(),
                            t.getTxnDate(),
                            t.getKind(),
                            r.getSuggestions());
                })
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

        String category = req.category() == null ? "" : req.category().trim().toUpperCase();
        if (!categories.existsByCodeIgnoreCaseAndActiveTrue(category)) {
            throw new IllegalArgumentException("Nieznana kategoria: " + req.category());
        }

        txn.setCategory(category);
        txn.setKind(req.kind() == null ? txn.getKind() : req.kind());
        txn.setCategorySource(CategorySource.MANUAL);
        txn.setConfidence(BigDecimal.ONE);
        txn.setNeedsReview(false);
        txns.save(txn);

        if (req.learnRule() && txn.getMerchantNorm() != null && !txn.getMerchantNorm().isBlank()) {
            rules.findByMatchTypeAndPattern(MatchType.EXACT, txn.getMerchantNorm())
                    .ifPresentOrElse(
                            existing -> {
                                existing.setCategory(category);
                                existing.setKind(txn.getKind());
                                rules.save(existing);
                            },
                            () -> {
                                MerchantRule rule = new MerchantRule();
                                rule.setMatchType(MatchType.EXACT);
                                rule.setPattern(txn.getMerchantNorm());
                                rule.setCategory(category);
                                rule.setKind(txn.getKind());
                                rules.save(rule);
                            });
        }

        item.setStatus(ReviewStatus.RESOLVED);
        item.setResolvedAt(Instant.now());
        reviews.save(item);
    }
}
