package pl.havronskyi.finance.stats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record PeriodReport(
        LocalDate from,
        LocalDate to,
        BigDecimal totalExpenses,
        BigDecimal totalIncome,
        BigDecimal net,
        Map<String, BigDecimal> byCategory,
        BigDecimal excludedInternalTransfers,
        int uncategorizedCount,
        List<String> warnings,
        List<String> nettedCounterparties
) {
}
