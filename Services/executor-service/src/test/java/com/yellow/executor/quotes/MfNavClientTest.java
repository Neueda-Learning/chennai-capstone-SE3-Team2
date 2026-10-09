package com.yellow.executor.quotes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.yellow.executor.config.MfNavProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The NAV client against a real HTTP server, so the JSON mapping and the
 * status handling are genuinely exercised. The bodies are the MF NAV
 * service's own shapes.
 */
class MfNavClientTest {

    private static final String KEY = "test-key-not-a-real-credential";
    private static final String PATH = "/nav/122639";

    private WireMockServer api;

    @BeforeEach
    void startApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
    }

    @AfterEach
    void stopApi() {
        api.stop();
    }

    private MfNavClient client(String key) {
        return new MfNavClient(new MfNavProperties(api.baseUrl(), key, Duration.ofSeconds(2), 3,
                Duration.ofMillis(10), Duration.ofMillis(20)), new ObjectMapper());
    }

    @Test
    @DisplayName("a 200 gives the NAV as both the buy and the sell price, read without going through double")
    void mapsTheNav() {
        api.stubFor(get(urlPathEqualTo(PATH)).willReturn(ok(body(false))));

        Quote quote = client(KEY).nav("122639");

        assertThat(quote.symbol(), is("122639"));
        assertThat(quote.buyPrice(), comparesEqualTo(new BigDecimal("88.2569")));
        assertThat(quote.sellPrice(), comparesEqualTo(new BigDecimal("88.2569")));
        assertThat(quote.asOf(), is(Instant.parse("2026-10-04T05:00:00Z")));
        assertThat(quote.marketState(), is("NAV 2026-10-01"));
        assertThat(quote.stale(), is(false));
        assertThat(quote.source(), is("cache"));
    }

    @Test
    @DisplayName("the API key travels in X-Api-Key")
    void sendsTheKey() {
        api.stubFor(get(urlPathEqualTo(PATH)).willReturn(ok(body(false))));

        client(KEY).nav("122639");

        api.verify(getRequestedFor(urlPathEqualTo(PATH)).withHeader("X-Api-Key", equalTo(KEY)));
    }

    @Test
    @DisplayName("a stale NAV is used as it is, marked stale, without asking again")
    void staleNavIsUsed() {
        api.stubFor(get(urlPathEqualTo(PATH)).willReturn(ok(body(true))));

        Quote quote = client(KEY).nav("122639");

        // A fund deals at its latest published NAV, however old the service
        // says it is; the flag travels with the price.
        assertThat(quote.stale(), is(true));
        assertThat(quote.price(), comparesEqualTo(new BigDecimal("88.2569")));
        api.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    @DisplayName("an unknown fund (404) is refused on the first attempt")
    void unknownFundIsRefused() {
        api.stubFor(get(urlPathEqualTo("/nav/INF010A01010")).willReturn(aResponse().withStatus(404)
                .withBody("{\"errorCode\":\"INS-404\",\"message\":\"Unknown ISIN or scheme code\"}")));

        assertThrows(QuoteUnavailableException.class, () -> client(KEY).nav("INF010A01010"));
        api.verify(1, getRequestedFor(urlPathEqualTo("/nav/INF010A01010")));
    }

    @Test
    @DisplayName("an identifier the service cannot read (422) is refused on the first attempt")
    void malformedIdentifierIsRefused() {
        api.stubFor(get(urlPathEqualTo("/nav/SCH100001")).willReturn(aResponse().withStatus(422)
                .withBody("{\"errorCode\":\"VAL-422\",\"message\":\"Malformed identifier\"}")));

        assertThrows(QuoteUnavailableException.class, () -> client(KEY).nav("SCH100001"));
        api.verify(1, getRequestedFor(urlPathEqualTo("/nav/SCH100001")));
    }

    @Test
    @DisplayName("a refused key (401) is not retried, and the failure never carries the key")
    void refusedKeyIsNotRetriedOrLeaked() {
        api.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(401)
                .withBody("{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}")));

        QuoteUnavailableException e = assertThrows(QuoteUnavailableException.class, () -> client(KEY).nav("122639"));

        assertThat(e.getMessage(), not(containsString(KEY)));
        api.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    @DisplayName("a 503 is retried, then the NAV that follows is used")
    void unavailableIsRetried() {
        api.stubFor(get(urlPathEqualTo(PATH)).inScenario("waking").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("awake"));
        api.stubFor(get(urlPathEqualTo(PATH)).inScenario("waking").whenScenarioStateIs("awake")
                .willReturn(ok(body(false))));

        assertThat(client(KEY).nav("122639").buyPrice(), comparesEqualTo(new BigDecimal("88.2569")));
        api.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    @DisplayName("without a key no request is made: fund orders are refused, and the reason says to set MF_NAV_API_KEY")
    void noKeyMeansNoRequest() {
        QuoteUnavailableException e = assertThrows(QuoteUnavailableException.class, () -> client("").nav("122639"));

        assertThat(e.getMessage(), containsString("MF_NAV_API_KEY"));
        api.verify(0, getRequestedFor(urlPathEqualTo(PATH)));
    }

    private static String body(boolean stale) {
        return """
                {
                  "data": {
                    "isin": "INF879O01027",
                    "schemeCode": "122639",
                    "schemeName": "Parag Parikh Flexi Cap Fund - Direct Plan - Growth",
                    "nav": 88.2569,
                    "navDate": "2026-10-01",
                    "isinType": "growth"
                  },
                  "meta": {
                    "asOf": "2026-10-04T05:00:00Z",
                    "disclaimer": "Educational data. Not for investment use.",
                    "symbol": "122639",
                    "source": "cache",
                    "stale": %s
                  }
                }
                """.formatted(stale);
    }
}
