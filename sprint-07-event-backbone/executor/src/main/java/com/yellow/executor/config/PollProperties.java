package com.yellow.executor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the market-data poller is configured.
 *
 * @param intervalSeconds from POLL_INTERVAL_SECONDS. What the operator asked
 *                        for; {@code PollSchedule} decides what is actually
 *                        safe to run
 * @param floorSeconds    the absolute minimum, whatever the arithmetic says.
 *                        60 because nothing faster survives being left running
 *                        overnight, and an executor left up after a session is
 *                        the ordinary case rather than the exception
 * @param batchSize       symbols per request. 25 is the API's hard cap, not a
 *                        tuning knob: 26 is a 400, not a truncation
 * @param enabled         lets a test or a session that only wants the fill path
 *                        stop the poller spending quota, without deleting the
 *                        bean and changing what is being tested
 */
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
            // Clamped rather than trusted. A value above 25 is a 400 from the
            // API on every cycle, which presents as the poller silently never
            // publishing anything.
            batchSize = 25;
        }
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
    }
}
