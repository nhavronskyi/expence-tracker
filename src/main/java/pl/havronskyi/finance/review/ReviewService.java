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

        applyCategory(txn, req);

        item.setStatus(ReviewStatus.RESOLVED);
        item.setResolvedAt(Instant.now());
        reviews.save(item);
    }

    /**
     * Lets any transaction's category/kind be corrected directly, outside the review queue -
     * e.g. moving a wrongly-classified internal transfer into a real expense category (or a
     * real expense that's actually an internal transfer) from the Stats page's transaction
     * drill-down, long after it was auto-categorized and never touched the review queue.
     */
    @Transactional
    public void recategorize(Long txnId, ResolveRequest req) {
        Txn txn = txns.findById(txnId)
                .orElseThrow(() -> new IllegalArgumentException("Brak transakcji " + txnId));
        applyCategory(txn, req);
    }

    private void applyCategory(Txn txn, ResolveRequest req) {
        String category = req.category() == null ? "" : req.category().trim().toUpperCase();
        if (!categories.existsByCodeIgnoreCaseAndActiveTrue(category)) {
            throw new IllegalArgumentException("Nieznana kategoria: " + req.category());
        }

        TxnKind kind = req.kind() == null ? txn.getKind() : req.kind();
        txn.setCategory(category);
        txn.setKind(kind);
        if (kind != TxnKind.INTERNAL_TRANSFER) {
            // Otherwise a transaction manually moved out of INTERNAL_TRANSFER keeps pointing
            // at a transfer group whose partner leg still thinks it's paired.
            txn.setTransferGroup(null);
        }
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
    }
}
