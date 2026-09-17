package com.yellow.executor.quotes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.executor.config.FauxnanceProperties;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Fauxnance client. The only thing in the platform that holds the API key.
 *
 * <h2>What is retried, and what is not</h2>
 *
 * Retrying the wrong thing is worse than not retrying: it spends quota that the
 * fill path needs and delays an answer the customer is waiting for. So the
 * split is explicit.
 *
 * <p>RETRIED, because a second ask might succeed:
 * <ul>
 *   <li>a connection failure or timeout</li>
 *   <li>429, waiting the {@code Retry-After} the API hands us rather than
 *       guessing a backoff</li>
 *   <li>503, the upstream Fauxnance depends on being unavailable</li>
 *   <li>202, a backfill in progress -- there is no price <em>yet</em></li>
 *   <li>a 200 whose {@code meta.stale} is true</li>
 * </ul>
 *
 * <p>That last one deserves its reason. A stale quote is a 200 with a number in
 * it, so it is easy to treat as success. It means the cached value is past its
 * freshness window <em>and no upstream could be reached to refresh it</em> --
 * an outage the API is papering over. Filling a customer's order against a
 * price known to be expired is the thing we would least like to explain, so it
 * is retried and then refused.
 *
 * <p>NOT RETRIED, because no number of attempts will help: 404 on an unknown
 * symbol, 400 on a malformed request, 401 or 403 on the key. These throw on the
 * first response.
 *
 * <p>NOT BRANCHED ON AT ALL: {@code meta.source} and {@code marketState}. A
 * quote's bid and ask are modelled from candle history on every response
 * whatever {@code source} says -- {@code spreadSource} has exactly one possible
 * value -- so refusing generated numbers would mean refusing to trade. And
 * Indian symbols report {@code marketState: "unknown"}, so a rule built on it
 * would be a rule built on noise. Both travel through to {@code market-data}
 * for consumers who want them.
 */
@Component
public class FauxnanceQuoteClient implements QuoteSource {

    private static final Logger log = LoggerFactory.getLogger(FauxnanceQuoteClient.class);

    /** The batch endpoint's hard cap. Exceeding it is a 400, not a truncation. */
    public static final int MAX_BATCH = 25;

    private static final String API_KEY_HEADER = "X-Api-Key";

    private final FauxnanceProperties config;
    private final QuotaCounter quota;
    private final ObjectMapper json;
    private final HttpClient http;

    public FauxnanceQuoteClient(FauxnanceProperties config, QuotaCounter quota, ObjectMapper json) {
        this.config = config;
        this.quota = quota;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Quote quote(String symbol) {
        String path = "/quotes/" + URLEncoder.encode(symbol, StandardCharsets.UTF_8);

        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            Outcome outcome = attempt(path, symbol);

            if (outcome.body != null && outcome.quote == null) {
                // A 200 that parsed but carried no quote. Nothing to retry.
                throw new QuoteUnavailableException(
                        "no price for " + symbol + ": response carried no quote", symbol, attempt);
            }

            if (outcome.quote != null) {
                JsonNode meta = outcome.body.path("meta");
                boolean stale = meta.path("stale").asBoolean(false);

                if (!stale) {
                    return outcome.quote;
                }
                // A 200 we refuse to use. See the class comment.
                log.warn("quote for {} is stale (source={}), attempt {} of {}",
                        symbol, meta.path("source").asText("unknown"),
                        attempt, config.maxAttempts());
                outcome = Outcome.retryable(Duration.ZERO, "stale quote");
            }

            if (!outcome.retryable) {
                throw new QuoteUnavailableException(
                        "no price for " + symbol + ": " + outcome.detail, symbol, attempt);
            }
            if (attempt < config.maxAttempts()) {
                sleep(backoffFor(attempt, outcome.retryAfter));
            }
        }

        throw new QuoteUnavailableException(
                "no usable price for " + symbol + " after " + config.maxAttempts() + " attempts",
                symbol, config.maxAttempts());
    }

    @Override
    public Map<String, Quote> quotes(List<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        if (symbols.size() > MAX_BATCH) {
            // The caller is responsible for chunking. Silently truncating here
            // would drop symbols with nothing in the logs to say which.
            throw new IllegalArgumentException(
                    "the batch endpoint takes at most " + MAX_BATCH + " symbols, asked for "
                            + symbols.size());
        }

        String path = "/quotes?symbols=" + URLEncoder.encode(
                String.join(",", symbols), StandardCharsets.UTF_8);

        Outcome outcome = attempt(path, String.join(",", symbols));
        if (outcome.body == null) {
            log.warn("batch quote request failed: {}", outcome.detail);
            return Map.of();
        }

        Map<String, Quote> found = new LinkedHashMap<>();
        for (JsonNode item : outcome.body.path("data").path("quotes")) {
            // Per-symbol errors are expected and must not fail the batch: one
            // unknown symbol should not cost the other 24 their update.
            if (item.hasNonNull("error")) {
                log.debug("no quote for {}: {}", item.path("symbol").asText(),
                        item.path("error").path("code").asText());
                continue;
            }
            JsonNode q = item.path("quote");
            if (q.isMissingNode()) {
                continue;
            }
            // In the batch response stale and source sit on the ITEM, not in
            // meta as they do on the single-quote response.
            found.put(item.path("symbol").asText(),
                    toQuote(q, item.path("stale").asBoolean(false),
                            item.path("source").asText("unknown")));
        }
        return found;
    }

    // ------------------------------------------------------------ one attempt

    private Outcome attempt(String path, String what) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + path))
                .header(API_KEY_HEADER, config.apiKey())
                .timeout(config.timeout())
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            quota.spend();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return Outcome.retryable(Duration.ZERO, "transport: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.fatal("interrupted");
        }

        int status = response.statusCode();

        // 202: the symbol is being backfilled. There is no price yet, but there
        // will be, so this is a wait rather than a refusal.
        if (status == 202) {
            return Outcome.retryable(Duration.ZERO, "backfill in progress");
        }
        if (status == 429) {
            // The API tells us how long to wait. Guessing would either hammer
            // it or idle longer than necessary.
            return Outcome.retryable(retryAfter(response), "rate limited");
        }
        if (status == 503) {
            return Outcome.retryable(Duration.ZERO, "upstream unavailable");
        }
        if (status >= 500) {
            return Outcome.retryable(Duration.ZERO, "server error " + status);
        }
        if (status == 404) {
            // The symbol is not in the Fauxnance registry. Every one of our
            // fictional Sprint 3 tickers lands here, and so does a typo.
            return Outcome.fatal("unknown symbol");
        }
        if (status >= 400) {
            // 401 and 403 mean the key is wrong or revoked. Retrying a rejected
            // credential just spends the rest of the budget failing.
            return Outcome.fatal("request refused, status " + status);
        }

        try {
            JsonNode body = json.readTree(response.body());
            JsonNode data = body.path("data");
            JsonNode meta = body.path("meta");

            // The two endpoints shape `data` differently: the single-quote
            // response puts the quote there directly, the batch puts a `quotes`
            // array. Only the single form is turned into a Quote here; the
            // batch caller walks the array itself, because it also has to read
            // stale and source from each item rather than from meta.
            Quote single = data.hasNonNull("ask")
                    ? toQuote(data, meta.path("stale").asBoolean(false),
                              meta.path("source").asText("unknown"))
                    : null;

            return Outcome.ok(body, single);
        } catch (Exception e) {
            log.warn("unparseable quote response for {}: {}", what, e.getMessage());
            return Outcome.fatal("unparseable response");
        }
    }

    // ------------------------------------------------------------- mapping

    private static Quote toQuote(JsonNode q, boolean stale, String source) {
        return new Quote(
                q.path("symbol").asText(),
                decimal(q, "price"),
                decimal(q, "bid"),
                decimal(q, "ask"),
                decimal(q, "spreadBps"),
                q.path("currency").asText(null),
                decimal(q, "change"),
                decimal(q, "changePercent"),
                decimal(q, "previousClose"),
                q.hasNonNull("asOf") ? Instant.parse(q.path("asOf").asText()) : null,
                q.path("marketState").asText("unknown"),
                stale,
                source);
    }

    /**
     * Read a number without going through double. A price is money, and
     * {@code asDouble} on 125102.70693446 is where a half-paisa goes missing.
     */
    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() || value.isTextual() ? new BigDecimal(value.asText()) : null;
    }

    // -------------------------------------------------------------- backoff

    /**
     * Honour {@code Retry-After} when the API sent one, otherwise exponential.
     * Either way capped: a consumer thread parked for minutes stops every
     * account on that partition, so a very large Retry-After is treated as
     * "give up soon" rather than "wait that long".
     */
    private Duration backoffFor(int attempt, Duration retryAfter) {
        Duration wait = retryAfter.isZero()
                ? config.initialBackoff().multipliedBy(1L << (attempt - 1))
                : retryAfter;
        return wait.compareTo(config.maxBackoff()) > 0 ? config.maxBackoff() : wait;
    }

    /** Retry-After is either delta-seconds or an HTTP-date. Both are legal. */
    private static Duration retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After").map(raw -> {
            try {
                return Duration.ofSeconds(Long.parseLong(raw.trim()));
            } catch (NumberFormatException notSeconds) {
                try {
                    Duration until = Duration.between(Instant.now(),
                            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                    return until.isNegative() ? Duration.ZERO : until;
                } catch (Exception notADate) {
                    return Duration.ZERO;
                }
            }
        }).orElse(Duration.ZERO);
    }

    private static void sleep(Duration wait) {
        if (wait.isZero() || wait.isNegative()) {
            return;
        }
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------- outcome

    /** One attempt's result: a quote, or a reason with a verdict on retrying. */
    private record Outcome(JsonNode body, Quote quote, boolean retryable,
                           Duration retryAfter, String detail) {

        static Outcome ok(JsonNode body, Quote quote) {
            return new Outcome(body, quote, false, Duration.ZERO, null);
        }

        static Outcome retryable(Duration retryAfter, String detail) {
            return new Outcome(null, null, true, retryAfter, detail);
        }

        static Outcome fatal(String detail) {
            return new Outcome(null, null, false, Duration.ZERO, detail);
        }
    }
}
