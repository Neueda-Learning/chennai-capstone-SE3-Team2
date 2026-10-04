package com.yellow.executor.quotes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.executor.config.MfNavProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * The MF NAV service client: the latest NAV for a fund, from AMFI's daily
 * file. The service answers in Fauxnance's envelope -- data plus
 * meta.stale/source/asOf -- and with the platform's error codes.
 *
 * What is retried and what is not:
 * <ul>
 *   <li>no answer, 429, 503, 5xx -- retried with a growing wait; the service
 *       may be waking up or briefly over its limit</li>
 *   <li>404 (unknown fund), 422 (not an ISIN or scheme code), 401 (key
 *       refused) -- refused at once; asking again cannot change the answer</li>
 *   <li>a stale NAV -- refused at once: a NAV changes once a day, so a second
 *       ask a second later returns the same stale number</li>
 * </ul>
 */
@Component
public class MfNavClient implements NavSource {

    private static final Logger log = LoggerFactory.getLogger(MfNavClient.class);

    private static final String API_KEY_HEADER = "X-Api-Key";

    private final MfNavProperties config;
    private final ObjectMapper json;
    private final HttpClient http;

    public MfNavClient(MfNavProperties config, ObjectMapper json) {
        this.config = config;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Quote nav(String identifier) {
        if (!config.hasKey()) {
            log.warn("fund {} cannot be priced: MF_NAV_API_KEY is not set", identifier);
            throw new QuoteUnavailableException(
                    "no NAV for " + identifier + ": MF_NAV_API_KEY is not set", identifier, 0);
        }

        String path = "/nav/" + URLEncoder.encode(identifier, StandardCharsets.UTF_8);
        String detail = "no attempt made";

        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            Outcome outcome = attempt(path, identifier);
            if (outcome.quote != null) {
                return outcome.quote;
            }
            detail = outcome.detail;
            if (!outcome.retryable) {
                throw new QuoteUnavailableException("no NAV for " + identifier + ": " + detail, identifier, attempt);
            }
            log.warn("NAV for {} not available ({}), attempt {} of {}", identifier, detail, attempt, config.maxAttempts());
            if (attempt < config.maxAttempts()) {
                sleep(backoffFor(attempt, outcome.retryAfter));
            }
        }
        throw new QuoteUnavailableException("no NAV for " + identifier + " after " + config.maxAttempts()
                + " attempts: " + detail, identifier, config.maxAttempts());
    }

    // ------------------------------------------------------------ one attempt

    private Outcome attempt(String path, String identifier) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + path))
                .header(API_KEY_HEADER, config.apiKey())
                .timeout(config.timeout())
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return Outcome.retry(Duration.ZERO, "transport: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.refuse("interrupted");
        }

        int status = response.statusCode();
        if (status == 429) {
            return Outcome.retry(retryAfter(response), "rate limited");
        }
        if (status >= 500) {
            return Outcome.retry(Duration.ZERO, "server error " + status);
        }
        if (status == 404) {
            return Outcome.refuse("unknown fund");
        }
        if (status == 422) {
            return Outcome.refuse("not an ISIN or AMFI scheme code");
        }
        if (status >= 400) {
            // 401: the key is wrong or revoked. Retrying it just fails again.
            return Outcome.refuse("request refused, status " + status);
        }

        try {
            JsonNode body = json.readTree(response.body());
            JsonNode data = body.path("data");
            JsonNode meta = body.path("meta");
            if (!data.hasNonNull("nav")) {
                return Outcome.refuse("response carried no NAV");
            }
            if (meta.path("stale").asBoolean(false)) {
                return Outcome.refuse("stale NAV, dated " + data.path("navDate").asText("unknown"));
            }
            // Read as text, never through double: a paisa must not go missing.
            BigDecimal nav = new BigDecimal(data.path("nav").asText());
            Instant asOf = meta.hasNonNull("asOf") ? Instant.parse(meta.path("asOf").asText()) : null;
            return Outcome.ok(Quote.ofNav(identifier, nav, asOf,
                    data.path("navDate").asText("unknown"), meta.path("source").asText("unknown")));
        } catch (Exception e) {
            log.warn("unparseable NAV response for {}: {}", identifier, e.getClass().getSimpleName());
            return Outcome.refuse("unparseable response");
        }
    }

    // -------------------------------------------------------------- backoff

    private Duration backoffFor(int attempt, Duration retryAfter) {
        Duration wait = retryAfter.isZero()
                ? config.initialBackoff().multipliedBy(1L << (attempt - 1))
                : retryAfter;
        return wait.compareTo(config.maxBackoff()) > 0 ? config.maxBackoff() : wait;
    }

    /** Retry-After in seconds, when the service sent one. */
    private static Duration retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After").map(raw -> {
            try {
                return Duration.ofSeconds(Long.parseLong(raw.trim()));
            } catch (NumberFormatException e) {
                return Duration.ZERO;
            }
        }).orElse(Duration.ZERO);
    }

    private static void sleep(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Outcome(Quote quote, boolean retryable, Duration retryAfter, String detail) {

        static Outcome ok(Quote quote) {
            return new Outcome(quote, false, Duration.ZERO, "ok");
        }

        static Outcome retry(Duration retryAfter, String detail) {
            return new Outcome(null, true, retryAfter, detail);
        }

        static Outcome refuse(String detail) {
            return new Outcome(null, false, Duration.ZERO, detail);
        }
    }
}
