package pl.havronskyi.finance.ingest;

import java.time.LocalDate;

/**
 * The result of parsing one row, still without business logic applied.
 * amountMinor: negative = debit, positive = credit.
 */
public record ParsedRow(
        int lineNo,
        String rawLine,
        LocalDate txnDate,
        LocalDate bookedDate,
        long amountMinor,
        String currency,
        String counterparty,
        String counterpartyIban,
        String description
) {
}
