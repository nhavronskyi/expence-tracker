package pl.havronskyi.finance.web;

import org.springframework.web.bind.annotation.*;
import pl.havronskyi.finance.domain.AccountScope;
import pl.havronskyi.finance.stats.MonthlyReport;
import pl.havronskyi.finance.stats.StatsService;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /** GET /api/stats/monthly?month=2026-07&scope=PERSONAL */
    @GetMapping("/monthly")
    public MonthlyReport monthly(@RequestParam String month,
                                 @RequestParam(defaultValue = "PERSONAL") AccountScope scope) {
        return statsService.monthly(YearMonth.parse(month), scope);
    }
}
