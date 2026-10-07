package com.yellow.trade.portfolio;

import com.yellow.trade.marketdata.FauxnanceBudget;
import com.yellow.trade.marketdata.MarketDataProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

/**
 * GET /health, public as the contract has it (security: []), outside /api/v1/
 * so the token filter never sees it. Status only, no customer data. Always
 * 200 while the service runs; a failing dependency degrades status.
 *
 * Fauxnance's remaining quota is this service's own count of what it has
 * spent today (FauxnanceBudget), so asking costs no quota at all.
 */
@RestController
public class PortfolioHealthController {

    private final JdbcTemplate jdbc;
    private final FauxnanceBudget budget;
    private final MarketDataProperties marketData;
    private final Clock clock;

    public PortfolioHealthController(JdbcTemplate jdbc, FauxnanceBudget budget, MarketDataProperties marketData,
                                     Clock clock) {
        this.jdbc = jdbc;
        this.budget = budget;
        this.marketData = marketData;
        this.clock = clock;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        HealthResponse.Dependency postgres = new HealthResponse.Dependency("postgres", postgres(), null);
        int remaining = budget.remaining();
        String fauxnanceStatus = marketData.fauxnanceApiKey() == null || marketData.fauxnanceApiKey().isBlank() ? "down"
                : remaining > 0 ? "ok" : "degraded";
        HealthResponse.Dependency fauxnance = new HealthResponse.Dependency("fauxnance", fauxnanceStatus, remaining);
        String status = "ok".equals(postgres.status()) && "ok".equals(fauxnance.status()) ? "ok" : "degraded";
        return new HealthResponse(status, List.of(postgres, fauxnance), clock.instant());
    }

    private String postgres() {
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1 ? "ok" : "degraded";
        } catch (RuntimeException e) {
            return "down";
        }
    }
}
