package com.yellow.executor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where the MF NAV service is and how hard we try. Unlike Fauxnance's, the key
 * is optional: without it the executor still trades stocks and ETFs, and a
 * fund order is rejected with no price and a log line saying why.
 */
@ConfigurationProperties(prefix = "mf-nav")
public record MfNavProperties(
        String baseUrl,
        String apiKey,
        Duration timeout,
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff) {

    /** The hosted service. Public, so it can be the default; the key cannot. */
    public static final String DEFAULT_BASE_URL = "https://mf-fauxnance-api.onrender.com";

    public MfNavProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        // Generous: the service is hosted, and a host that sleeps when idle
        // takes a while to answer the first request of the day.
        if (timeout == null) {
            timeout = Duration.ofSeconds(20);
        }
        if (maxAttempts < 1) {
            maxAttempts = 3;
        }
        if (initialBackoff == null) {
            initialBackoff = Duration.ofSeconds(1);
        }
        if (maxBackoff == null) {
            maxBackoff = Duration.ofSeconds(5);
        }
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
