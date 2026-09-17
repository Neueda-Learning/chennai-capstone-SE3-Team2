package com.yellow.executor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where Fauxnance is and how hard we try.
 *
 * <p>THE KEY IS NOT IN HERE WITH A DEFAULT, AND THAT IS THE POINT. It resolves
 * from FAUXNANCE_API_KEY in the environment and has no fallback, so the
 * application refuses to start without one rather than running and failing
 * every quote at the first order. It appears in no properties file, no YAML
 * file and no Java constant -- the same rule the platform already applies to
 * JWT_SECRET, and for the same reason: a default credential is a credential
 * that ships.
 *
 * @param baseUrl      the API root, including /v1
 * @param apiKey       from FAUXNANCE_API_KEY. No default, ever
 * @param timeout      per-request timeout
 * @param maxAttempts  total tries for one symbol, first attempt included
 * @param initialBackoff doubled after each retry
 * @param maxBackoff   ceiling, so a large Retry-After cannot park a consumer
 *                     thread for minutes while a partition goes unread
 * @param dailyQuota   requests per key per day, resetting 00:00 UTC
 * @param fillReserve  requests held back from the poller for the fill path
 */
@ConfigurationProperties(prefix = "fauxnance")
public record FauxnanceProperties(
        String baseUrl,
        String apiKey,
        Duration timeout,
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        int dailyQuota,
        int fillReserve) {

    public FauxnanceProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "FAUXNANCE_API_KEY is not set. The executor prices every order against "
                            + "Fauxnance and cannot start without a key.");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("fauxnance.base-url is not set");
        }
        // Defaults, so application.yml carries only what a deployment changes.
        if (timeout == null) {
            timeout = Duration.ofSeconds(10);
        }
        if (maxAttempts < 1) {
            maxAttempts = 3;
        }
        if (initialBackoff == null) {
            initialBackoff = Duration.ofMillis(500);
        }
        if (maxBackoff == null) {
            maxBackoff = Duration.ofSeconds(5);
        }
        if (dailyQuota < 1) {
            dailyQuota = 2000;
        }
        if (fillReserve < 0) {
            fillReserve = 200;
        }
    }

    /** The most requests the poller may spend, leaving the fill path its reserve. */
    public int pollerBudget() {
        return dailyQuota - fillReserve;
    }
}
