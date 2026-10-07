package com.yellow.trade.portfolio;

import java.time.Instant;
import java.util.List;

/** GET /health (contracts/portfolio-api.yaml): status only, no customer data. */
public record HealthResponse(String status, List<Dependency> dependencies, Instant asOf) {

    /** @param quotaRemaining Fauxnance only: requests this service may still spend today, by its own count */
    public record Dependency(String name, String status, Integer quotaRemaining) {
    }
}
