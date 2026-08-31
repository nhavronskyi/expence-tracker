package pl.havronskyi.finance.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
     * GET /api/stats/range?from=2026-07-01&to=2026-07-31
     */
    @GetMapping("/range")
    public ResponseEntity<PeriodReport> range(@RequestHeader("X-Workspace-Id") Long workspaceId,
                                              @RequestParam String from,
                                              @RequestParam String to) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        if (fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(statsService.forRange(workspaceId, fromDate, toDate));
    }

    @GetMapping("/count")
    public Map<String, Long> count(@RequestHeader("X-Workspace-Id") Long workspaceId) {
        return Map.of("total", statsService.totalTransactionCount(workspaceId));
    }

    /**
     * GET /api/stats/transactions?category=VITA&from=2026-08-01&to=2026-08-31
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<CategoryTransaction>> transactionsForCategory(
            @RequestHeader("X-Workspace-Id") Long workspaceId,
            @RequestParam String category,
            @RequestParam String from,
            @RequestParam String to) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        if (fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(statsService.transactionsForCategory(workspaceId, category, fromDate, toDate));
    }

    /**
     * GET /api/stats/transfers?from=2026-08-01&to=2026-08-31
     */
    @GetMapping("/transfers")
    public ResponseEntity<List<CategoryTransaction>> transfers(@RequestHeader("X-Workspace-Id") Long workspaceId,
                                                                @RequestParam String from,
                                                                @RequestParam String to) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        if (fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(statsService.transfersForRange(workspaceId, fromDate, toDate));
    }
}
