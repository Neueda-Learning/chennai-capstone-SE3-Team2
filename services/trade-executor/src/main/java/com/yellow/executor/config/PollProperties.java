package com.yellow.executor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** How the market-data poller is configured. */
@ConfigurationProperties(prefix = "poller")
public record PollProperties(
        long intervalSeconds,
        long floorSeconds,
        int batchSize,
        Boolean enabled) {

    public PollProperties {
        if (intervalSeconds <= 0) {
            intervalSeconds = 60;
        }
        if (floorSeconds <= 0) {
            floorSeconds = 60;
        }
        if (batchSize <= 0 || batchSize > 25) {
            // Clamped rather than trusted. A value above 25 is a 400 from
            // the API on every cycle, which presents as the poller silently
            batchSize = 25;
        }
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
    }
}
