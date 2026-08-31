package pl.havronskyi.finance.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.havronskyi.finance.domain.AccountScope;
import pl.havronskyi.finance.stats.CategoryTransaction;
import pl.havronskyi.finance.stats.PeriodReport;
import pl.havronskyi.finance.stats.StatsService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * GET /api/stats/range?from=2026-07-01&to=2026-07-31&scope=PERSONAL
     */
    @GetMapping("/range")
    public ResponseEntity<PeriodReport> range(@RequestParam String from,
                                              @RequestParam String to,
                                              @RequestParam(defaultValue = "PERSONAL") AccountScope scope) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        if (fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(statsService.forRange(fromDate, toDate, scope));
    }

    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("total", statsService.totalTransactionCount());
    }

    /**
     * GET /api/stats/transactions?category=VITA&from=2026-08-01&to=2026-08-31&scope=PERSONAL
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<CategoryTransaction>> transactionsForCategory(
            @RequestParam String category,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "PERSONAL") AccountScope scope) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        if (fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(statsService.transactionsForCategory(category, fromDate, toDate, scope));
    }
}
