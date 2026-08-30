package pl.havronskyi.finance.domain;

/**
 * INTERNAL_TRANSFER never enters the expense or income totals.
 * It's the one thing that breaks the math, so it's determined deterministically.
 */
public enum TxnKind {EXPENSE, INCOME, INTERNAL_TRANSFER, UNKNOWN}
