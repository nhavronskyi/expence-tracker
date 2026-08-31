package pl.havronskyi.finance.review;

import pl.havronskyi.finance.domain.TxnKind;

/**
 * learnRule=true creates a rule on the normalized merchant name,
 * so the same transaction won't come back next month.
 */
public record ResolveRequest(String category, TxnKind kind, boolean learnRule) {
}
