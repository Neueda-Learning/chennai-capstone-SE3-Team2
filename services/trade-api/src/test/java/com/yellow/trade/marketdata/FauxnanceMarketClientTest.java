package com.yellow.trade.marketdata;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FauxnanceMarketClientTest {

    private StubUpstream fauxnance;
    private FauxnanceMarketClient client;

    @BeforeEach
    void start() throws IOException {
        fauxnance = new StubUpstream();
        client = new FauxnanceMarketClient(RestClient.builder(),
                MarketDataPropertiesFixture.with(fauxnance.baseUrl(), "a-cohort-key", "http://unused", ""));
    }

    @AfterEach
    void stop() {
        fauxnance.close();
    }

    @Test
    @DisplayName("asks for a batch in one request, with the key in the header and never in the URL")
    void oneRequestPerBatch() {
        fauxnance.reply = BATCH;

        client.quotes(List.of("MRF.NS", "ITC.NS", "NOPE.NS"));

        assertThat(fauxnance.requests).containsExactly("/quotes?symbols=MRF.NS,ITC.NS,NOPE.NS");
        assertThat(fauxnance.apiKeys).containsExactly("a-cohort-key");
    }

    @Test
    @DisplayName("reads each quote with its own stale flag, and leaves out a symbol Fauxnance does not know")
    void readsTheBatch() {
        fauxnance.reply = BATCH;

        Map<String, PriceQuote> quotes = client.quotes(List.of("MRF.NS", "ITC.NS", "NOPE.NS"));

        assertThat(quotes).containsOnlyKeys("MRF.NS", "ITC.NS");
        PriceQuote mrf = quotes.get("MRF.NS");
        assertThat(mrf.price()).isEqualByComparingTo("123525");
        assertThat(mrf.change()).isEqualByComparingTo("-1755");
        assertThat(mrf.changePercent()).isEqualByComparingTo("-1.40");
        assertThat(mrf.previousClose()).isEqualByComparingTo("125280");
        assertThat(mrf.currency()).isEqualTo("INR");
        assertThat(mrf.asOf()).isEqualTo(Instant.parse("2026-10-01T09:59:51Z"));
        assertThat(mrf.stale()).isTrue();
        assertThat(quotes.get("ITC.NS").stale()).isFalse();
    }

    @Test
    @DisplayName("a refused or failed batch is pricing unavailable, not an empty answer")
    void failures() {
        for (int status : new int[] {401, 429, 500, 503}) {
            fauxnance.status = status;
            fauxnance.reply = "{\"error\":{\"code\":\"X\"}}";

            assertThatThrownBy(() -> client.quotes(List.of("MRF.NS")))
                    .isInstanceOf(PricingUnavailableException.class);
        }
    }

    @Test
    @DisplayName("nothing listening is pricing unavailable")
    void unreachable() {
        FauxnanceMarketClient nowhere = new FauxnanceMarketClient(RestClient.builder(),
                MarketDataPropertiesFixture.with("http://127.0.0.1:1", "k", "http://unused", ""));

        assertThatThrownBy(() -> nowhere.quotes(List.of("MRF.NS")))
                .isInstanceOf(PricingUnavailableException.class);
    }

    @Test
    @DisplayName("daily candles for a date range, oldest first, volume optional")
    void candles() {
        fauxnance.reply = CANDLES;

        List<Candle> candles = client.candles("MRF.NS", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-06"));

        assertThat(fauxnance.requests).containsExactly("/candles/MRF.NS?from=2026-09-01&to=2026-10-06");
        assertThat(candles).hasSize(2);
        Candle first = candles.get(0);
        assertThat(first.date()).isEqualTo(LocalDate.parse("2026-10-05"));
        assertThat(first.open()).isEqualByComparingTo("125000");
        assertThat(first.high()).isEqualByComparingTo("126100.5");
        assertThat(first.low()).isEqualByComparingTo("124000");
        assertThat(first.close()).isEqualByComparingTo("125280");
        assertThat(first.volume()).isEqualTo(8541L);
        assertThat(candles.get(1).volume()).isNull();
    }

    @Test
    @DisplayName("prices arrive in paise: the float noise in Fauxnance's numbers is rounded to two places")
    void roundsToPaise() {
        fauxnance.reply = """
                {"data": {"quotes": [{"symbol": "BAJAJ-AUTO.NS", "source": "synthetic", "stale": false,
                  "quote": {"price": 10031.6797195, "bid": 10030.1, "ask": 10033.255, "currency": "INR",
                            "change": 12.3456, "changePercent": -0.6419624217118998, "previousClose": 10019.33}}]},
                 "meta": {}}
                """;
        PriceQuote quote = client.quotes(List.of("BAJAJ-AUTO.NS")).get("BAJAJ-AUTO.NS");
        assertThat(quote.price()).isEqualByComparingTo("10031.68");
        assertThat(quote.price().scale()).isEqualTo(2);
        assertThat(quote.ask()).isEqualByComparingTo("10033.26");
        assertThat(quote.changePercent()).isEqualByComparingTo("-0.64");

        fauxnance.reply = """
                {"data": {"candles": [{"date": "2026-09-07", "open": 1020.7999877929688, "high": 1021.7000122070312,
                  "low": 1000.5999755859375, "close": 1005.9000244140625, "volume": 10217273}]}, "meta": {}}
                """;
        Candle candle = client.candles("SBIN.NS", LocalDate.parse("2026-09-07"), LocalDate.parse("2026-09-07")).get(0);
        assertThat(candle.open().toPlainString()).isEqualTo("1020.80");
        assertThat(candle.close().toPlainString()).isEqualTo("1005.90");
    }

    @Test
    @DisplayName("a symbol Fauxnance does not know has no candles, rather than failing the page")
    void unknownSymbolHasNoCandles() {
        fauxnance.status = 404;
        fauxnance.reply = "{\"error\":{\"code\":\"SYMBOL_NOT_FOUND\"}}";

        assertThat(client.candles("NOPE.NS", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-06"))).isEmpty();
    }

    @Test
    @DisplayName("a backfill still running is pricing unavailable for now")
    void backfillInProgress() {
        fauxnance.status = 202;
        fauxnance.reply = "{\"data\":{\"jobId\":\"j1\"}}";

        assertThatThrownBy(() -> client.candles("MRF.NS", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-06")))
                .isInstanceOf(PricingUnavailableException.class);
    }

    @Test
    @DisplayName("an M&M style symbol is encoded in the path and the query")
    void encodesSymbols() {
        fauxnance.reply = "{\"data\":{\"quotes\":[]},\"meta\":{}}";

        client.quotes(List.of("M&M.NS"));

        assertThat(fauxnance.rawRequests).containsExactly("/quotes?symbols=M%26M.NS");
    }

    private static final String BATCH = """
            {"data": {"quotes": [
              {"symbol": "MRF.NS", "source": "cache", "stale": true,
               "quote": {"symbol": "MRF.NS", "price": 123525, "bid": 123500, "ask": 123550, "currency": "INR",
                         "change": -1755, "changePercent": -1.40, "previousClose": 125280,
                         "asOf": "2026-10-01T09:59:51Z", "marketState": "unknown"}},
              {"symbol": "ITC.NS", "source": "upstream:yahoo", "stale": false,
               "quote": {"symbol": "ITC.NS", "price": 266.65, "bid": 266.6, "ask": 266.7, "currency": "INR",
                         "change": -2.25, "changePercent": -0.84, "previousClose": 268.9,
                         "asOf": "2026-10-06T04:19:31Z", "marketState": "open"}},
              {"symbol": "NOPE.NS", "error": {"code": "SYMBOL_NOT_FOUND", "message": "Symbol was not recognized."}}
            ]}, "meta": {"asOf": "2026-10-06T04:20:00Z"}}
            """;

    private static final String CANDLES = """
            {"data": {"candles": [
              {"date": "2026-10-05", "open": 125000, "high": 126100.5, "low": 124000, "close": 125280,
               "adjclose": 125280, "volume": 8541, "synthetic": false},
              {"date": "2026-10-06", "open": 123984.8, "high": 124933.7, "low": 121301.9, "close": 122237.4,
               "adjclose": 122237.4, "volume": null, "synthetic": true}
            ]}, "meta": {"symbol": "MRF.NS", "source": "mixed"}}
            """;
}
