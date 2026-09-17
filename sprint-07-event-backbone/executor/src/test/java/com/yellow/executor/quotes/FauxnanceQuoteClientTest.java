package com.yellow.executor.quotes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.yellow.executor.config.FauxnanceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The quote client against a real HTTP server rather than a mocked client, so
 * that the JSON mapping, the status handling and the Retry-After parsing are
 * all genuinely exercised. A mocked client would prove only that the code calls
 * the methods it calls.
 *
 * <p>The response bodies are real Fauxnance responses, copied from the live API.
 */
class FauxnanceQuoteClientTest {

    private static final String KEY = "test-key-not-a-real-credential";

    private WireMockServer api;
    private FauxnanceQuoteClient client;

    @BeforeEach
    void startApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        client = new FauxnanceQuoteClient(props(), new QuotaCounter(Clock.systemUTC()), new ObjectMapper());
    }

    @AfterEach
    void stopApi() {
        api.stop();
    }

    // ------------------------------------------------------------- the happy path

    @Test
    @DisplayName("a 200 maps every field, and reads prices without going through double")
    void mapsAFullQuote() {
        api.stubFor(get(urlPathEqualTo("/quotes/MRF.NS"))
                .willReturn(ok(freshQuote())));

        Quote quote = client.quote("MRF.NS");

        assertThat(quote.symbol(), is("MRF.NS"));
        // The full precision survives. Parsing through double would give
        // 125102.70693445999 here, and money does not round-trip through binary
        // floating point.
        assertThat(quote.price(), comparesEqualTo(new BigDecimal("125102.70693446")));
        assertThat(quote.bid(), comparesEqualTo(new BigDecimal("125083.94")));
        assertThat(quote.ask(), comparesEqualTo(new BigDecimal("125121.48")));
        assertThat(quote.currency(), is("INR"));
        assertThat(quote.marketState(), is("unknown"));
        assertThat(quote.asOf(), is(Instant.parse("2026-09-17T05:05:00Z")));
        // From meta, not data, on the single-quote response.
        assertThat(quote.stale(), is(false));
        assertThat(quote.source(), is("synthetic"));
    }

    @Test
    @DisplayName("the API key travels in X-Api-Key")
    void sendsTheApiKey() {
        api.stubFor(get(urlPathEqualTo("/quotes/ITC.NS")).willReturn(ok(freshQuote())));

        client.quote("ITC.NS");

        api.verify(getRequestedFor(urlPathEqualTo("/quotes/ITC.NS"))
                .withHeader("X-Api-Key", equalTo(KEY)));
    }

    @Test
    @DisplayName("a synthetic quote is used: every bid and ask is modelled, so refusing them refuses trading")
    void syntheticQuotesAreUsed() {
        api.stubFor(get(urlPathEqualTo("/quotes/MRF.NS"))
                .willReturn(ok(quoteBody("synthetic", false))));

        // source is not a reason to refuse. Only stale is.
        assertThat(client.quote("MRF.NS").source(), is("synthetic"));
    }

    // ------------------------------------------------------------- retried

    @Test
    @DisplayName("a stale quote is retried, and a fresh one on the second attempt is used")
    void staleIsRetriedThenSucceeds() {
        api.stubFor(get(urlPathEqualTo("/quotes/MRF.NS"))
                .inScenario("staleness").whenScenarioStateIs("Started")
                .willReturn(ok(quoteBody("cache", true)))
                .willSetStateTo("refreshed"));
        api.stubFor(get(urlPathEqualTo("/quotes/MRF.NS"))
                .inScenario("staleness").whenScenarioStateIs("refreshed")
                .willReturn(ok(quoteBody("upstream:yfinance", false))));

        Quote quote = client.quote("MRF.NS");

        assertThat(quote.stale(), is(false));
        api.verify(2, getRequestedFor(urlPathEqualTo("/quotes/MRF.NS")));
    }

    @Test
    @DisplayName("a quote stale on every attempt exhausts the budget and refuses to price")
    void permanentlyStaleThrows() {
        api.stubFor(get(urlPathEqualTo("/quotes/MRF.NS"))
                .willReturn(ok(quoteBody("cache", true))));

        QuoteUnavailableException thrown = assertThrows(QuoteUnavailableException.class,
                () -> client.quote("MRF.NS"));

        // We would rather reject the order than fill it against a price the API
        // itself says it could not refresh.
        assertThat(thrown.attempts(), is(3));
        api.verify(3, getRequestedFor(urlPathEqualTo("/quotes/MRF.NS")));
    }

    @Test
    @DisplayName("a 503 is retried, then succeeds")
    void upstreamUnavailableIsRetried() {
        api.stubFor(get(urlPathEqualTo("/quotes/TCS.NS"))
                .inScenario("outage").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        api.stubFor(get(urlPathEqualTo("/quotes/TCS.NS"))
                .inScenario("outage").whenScenarioStateIs("recovered")
                .willReturn(ok(freshQuote())));

        assertThat(client.quote("TCS.NS").symbol(), is("MRF.NS"));
        api.verify(2, getRequestedFor(urlPathEqualTo("/quotes/TCS.NS")));
    }

    @Test
    @DisplayName("a 202 backfill is retried: there is no price yet, but there will be")
    void backfillIsRetried() {
        api.stubFor(get(urlPathEqualTo("/quotes/ITC.NS"))
                .inScenario("backfill").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(202))
                .willSetStateTo("done"));
        api.stubFor(get(urlPathEqualTo("/quotes/ITC.NS"))
                .inScenario("backfill").whenScenarioStateIs("done")
                .willReturn(ok(freshQuote())));

        client.quote("ITC.NS");
        api.verify(2, getRequestedFor(urlPathEqualTo("/quotes/ITC.NS")));
    }

    @Test
    @DisplayName("a 429 waits the Retry-After the API sent, rather than guessing")
    void rateLimitHonoursRetryAfter() {
        api.stubFor(get(urlPathEqualTo("/quotes/ITC.NS"))
                .inScenario("quota").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1"))
                .willSetStateTo("allowed"));
        api.stubFor(get(urlPathEqualTo("/quotes/ITC.NS"))
                .inScenario("quota").whenScenarioStateIs("allowed")
                .willReturn(ok(freshQuote())));

        long start = System.currentTimeMillis();
        client.quote("ITC.NS");
        long waited = System.currentTimeMillis() - start;

        // The configured initial backoff is 10ms, so a wait of about a second
        // can only have come from the header.
        assertThat(waited >= 900, is(true));
    }

    // --------------------------------------------------------- not retried

    @Test
    @DisplayName("a 404 throws on the first attempt: no number of retries invents a symbol")
    void unknownSymbolIsNotRetried() {
        api.stubFor(get(urlPathEqualTo("/quotes/APEX")).willReturn(aResponse().withStatus(404)));

        QuoteUnavailableException thrown = assertThrows(QuoteUnavailableException.class,
                () -> client.quote("APEX"));

        assertThat(thrown.attempts(), is(1));
        assertThat(thrown.getMessage(), containsString("unknown symbol"));
        // This is the path every fictional Sprint 3 ticker takes, and it is how
        // the no-price demonstration is given at the review.
        api.verify(1, getRequestedFor(urlPathEqualTo("/quotes/APEX")));
    }

    @Test
    @DisplayName("a 401 throws on the first attempt: retrying a rejected key just burns the budget")
    void rejectedKeyIsNotRetried() {
        api.stubFor(get(urlPathEqualTo("/quotes/ITC.NS")).willReturn(aResponse().withStatus(401)));

        assertThrows(QuoteUnavailableException.class, () -> client.quote("ITC.NS"));
        api.verify(1, getRequestedFor(urlPathEqualTo("/quotes/ITC.NS")));
    }

    @Test
    @DisplayName("the failure never carries the API key, because it reaches a log")
    void failureDoesNotLeakTheKey() {
        api.stubFor(get(urlPathEqualTo("/quotes/ITC.NS")).willReturn(aResponse().withStatus(403)));

        QuoteUnavailableException thrown = assertThrows(QuoteUnavailableException.class,
                () -> client.quote("ITC.NS"));

        assertThat(thrown.getMessage(), not(containsString(KEY)));
    }

    // -------------------------------------------------------------- batch

    @Test
    @DisplayName("a batch reads stale and source from the item, not from meta")
    void batchReadsItemLevelStaleness() {
        api.stubFor(get(urlPathEqualTo("/quotes")).willReturn(ok(batchBody())));

        Map<String, Quote> quotes = client.quotes(List.of("MRF.NS", "ITC.NS", "APEX"));

        assertThat(quotes.get("MRF.NS").stale(), is(false));
        assertThat(quotes.get("MRF.NS").source(), is("cache"));
        assertThat(quotes.get("ITC.NS").stale(), is(true));
    }

    @Test
    @DisplayName("one unknown symbol does not cost the others their update")
    void perSymbolErrorDoesNotFailTheBatch() {
        api.stubFor(get(urlPathEqualTo("/quotes")).willReturn(ok(batchBody())));

        Map<String, Quote> quotes = client.quotes(List.of("MRF.NS", "ITC.NS", "APEX"));

        assertThat(quotes.size(), is(2));
        assertThat(quotes, hasKey("MRF.NS"));
        assertThat(quotes, not(hasKey("APEX")));
    }

    @Test
    @DisplayName("the whole batch costs one request, whatever the symbol count")
    void batchIsOneRequest() {
        api.stubFor(get(urlPathEqualTo("/quotes")).willReturn(ok(batchBody())));

        client.quotes(List.of("MRF.NS", "ITC.NS", "APEX"));

        api.verify(1, getRequestedFor(urlPathEqualTo("/quotes")));
    }

    @Test
    @DisplayName("more than 25 symbols is refused rather than silently truncated")
    void oversizedBatchIsRefused() {
        List<String> tooMany = java.util.stream.IntStream.range(0, 26)
                .mapToObj(i -> "SYM" + i + ".NS").toList();

        // Truncating would drop symbols with nothing in the logs to say which,
        // and they would simply stop updating. The caller chunks.
        assertThrows(IllegalArgumentException.class, () -> client.quotes(tooMany));
    }

    @Test
    @DisplayName("an empty symbol set makes no request at all")
    void emptyBatchMakesNoRequest() {
        assertThat(client.quotes(List.of()).isEmpty(), is(true));
        api.verify(0, getRequestedFor(urlEqualTo("/quotes")));
    }

    // ----------------------------------------------------------- fixtures

    private FauxnanceProperties props() {
        return new FauxnanceProperties(
                "http://localhost:" + api.port(), KEY,
                Duration.ofSeconds(5), 3,
                Duration.ofMillis(10), Duration.ofSeconds(2),
                2000, 200);
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder ok(String body) {
        return aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    /** A real MRF.NS response, copied from the live API. */
    private static String freshQuote() {
        return quoteBody("synthetic", false);
    }

    private static String quoteBody(String source, boolean stale) {
        return """
                {
                  "data": {
                    "symbol": "MRF.NS",
                    "price": 125102.70693446,
                    "bid": 125083.94,
                    "ask": 125121.48,
                    "spreadBps": 3,
                    "currency": "INR",
                    "change": 92.70693446,
                    "changePercent": 0.0741596147988161,
                    "previousClose": 125010,
                    "asOf": "2026-09-17T05:05:00Z",
                    "marketState": "unknown"
                  },
                  "meta": {
                    "asOf": "2026-09-17T05:05:00Z",
                    "disclaimer": "Educational data. Not for investment use.",
                    "symbol": "MRF.NS",
                    "source": "%s",
                    "stale": %s,
                    "spreadSource": "modelled"
                  }
                }
                """.formatted(source, stale);
    }

    private static String batchBody() {
        return """
                {
                  "data": {
                    "quotes": [
                      {"symbol": "MRF.NS", "source": "cache", "stale": false,
                       "quote": {"symbol": "MRF.NS", "price": 125102.70, "bid": 125083.94,
                                 "ask": 125121.48, "spreadBps": 3, "currency": "INR",
                                 "change": 92.70, "changePercent": 0.074, "previousClose": 125010,
                                 "asOf": "2026-09-17T05:05:00Z", "marketState": "unknown"}},
                      {"symbol": "ITC.NS", "source": "cache", "stale": true,
                       "quote": {"symbol": "ITC.NS", "price": 412.35, "bid": 412.29,
                                 "ask": 412.41, "spreadBps": 3, "currency": "INR",
                                 "change": 1.15, "changePercent": 0.28, "previousClose": 411.20,
                                 "asOf": "2026-09-17T05:05:00Z", "marketState": "unknown"}},
                      {"symbol": "APEX",
                       "error": {"code": "SYMBOL_NOT_FOUND", "message": "Symbol was not recognized.",
                                 "details": {}}}
                    ]
                  },
                  "meta": {
                    "asOf": "2026-09-17T05:05:00Z",
                    "disclaimer": "Educational data. Not for investment use.",
                    "spreadSource": "modelled"
                  }
                }
                """;
    }
}
