package com.yellow.trade.marketdata;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfNavMarketClientTest {

    private StubUpstream nav;
    private MfNavMarketClient client;

    @BeforeEach
    void start() throws IOException {
        nav = new StubUpstream();
        client = new MfNavMarketClient(RestClient.builder(),
                MarketDataPropertiesFixture.with("http://unused", "", nav.baseUrl(), "a-nav-key"));
    }

    @AfterEach
    void stop() {
        nav.close();
    }

    @Test
    @DisplayName("asks for up to 25 funds in one request, by scheme code, with the key in the header")
    void oneRequestPerBatch() {
        nav.reply = BATCH;

        client.navs(List.of("122639", "135764", "999999"));

        assertThat(nav.requests).containsExactly("/nav?isins=122639,135764,999999");
        assertThat(nav.apiKeys).containsExactly("a-nav-key");
    }

    @Test
    @DisplayName("a NAV is the price, dated by its NAV date in India; an unknown fund is left out")
    void readsTheBatch() {
        nav.reply = BATCH;

        Map<String, PriceQuote> navs = client.navs(List.of("122639", "135764", "999999"));

        assertThat(navs).containsOnlyKeys("122639", "135764");
        PriceQuote ppfas = navs.get("122639");
        assertThat(ppfas.price()).isEqualByComparingTo("88.7620");
        assertThat(ppfas.currency()).isEqualTo("INR");
        assertThat(ppfas.asOf()).isEqualTo(Instant.parse("2026-10-04T18:30:00Z"));
        assertThat(ppfas.change()).isNull();
        assertThat(ppfas.stale()).isFalse();
        assertThat(navs.get("135764").stale()).isTrue();
    }

    @Test
    @DisplayName("a refused or failed batch is pricing unavailable")
    void failures() {
        for (int status : new int[] {401, 429, 503}) {
            nav.status = status;
            nav.reply = "{\"errorCode\":\"X\",\"message\":\"no\"}";

            assertThatThrownBy(() -> client.navs(List.of("122639")))
                    .isInstanceOf(PricingUnavailableException.class);
        }
    }

    private static final String BATCH = """
            {"data": {"navs": [
              {"isin": "122639", "source": "cache", "stale": false,
               "nav": {"isin": "INF879O01027", "schemeCode": "122639",
                       "schemeName": "Parag Parikh Flexi Cap Fund - Direct Plan - Growth",
                       "nav": 88.7620, "navDate": "2026-10-05"}},
              {"isin": "135764", "source": "cache", "stale": true,
               "nav": {"isin": "INF846K01WR4", "schemeCode": "135764",
                       "schemeName": "Axis Children's Fund - Direct Plan - Growth Option",
                       "nav": 29.6667, "navDate": "2026-10-03"}},
              {"isin": "999999", "error": {"errorCode": "INS-404", "message": "Unknown ISIN or scheme code"}}
            ]}, "meta": {"asOf": "2026-10-06T06:43:19Z"}}
            """;
}
