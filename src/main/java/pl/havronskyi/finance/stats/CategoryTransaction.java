package pl.havronskyi.finance.stats;

import pl.havronskyi.finance.domain.TxnKind;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CategoryTransaction(
        Long txnId,
        LocalDate txnDate,
        String merchant,
        String description,
        BigDecimal amount,
        String currency,
        TxnKind kind
) {
}
