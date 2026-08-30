package pl.havronskyi.finance.stats;

import pl.havronskyi.finance.domain.Category;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public record MonthlyReport(
        YearMonth month,
        String scope,
        BigDecimal totalExpenses,
        BigDecimal totalIncome,
        BigDecimal net,
        Map<Category, BigDecimal> byCategory,
        BigDecimal excludedInternalTransfers,
        int uncategorizedCount,
        List<String> warnings
) { }
