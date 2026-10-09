package com.yellow.trade.marketdata;

import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.TradableInstrumentRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceServiceTest {

    /** A clock the test moves by hand. */
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-06T05:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MovableClock clock = new MovableClock();
    private final InstrumentMapper instruments = mock(InstrumentMapper.class);
    private final FauxnanceMarketClient fauxnance = mock(FauxnanceMarketClient.class);
    private final MfNavMarketClient nav = mock(MfNavMarketClient.class);
    private PriceService prices;

    @BeforeEach
    void setUp() {
        prices = service("a-key", 400);
    }

    private PriceService service(String fauxnanceKey, int budget) {
        MarketDataProperties properties =
                MarketDataPropertiesFixture.budget("http://f", fauxnanceKey, "http://n", "k", budget);
        return new PriceService(instruments, fauxnance, nav, new FauxnanceBudget(properties, clock),
                properties, clock);
    }

    private static TradableInstrumentRow row(String symbol, String type) {
        TradableInstrumentRow row = new TradableInstrumentRow();
        row.setSymbol(symbol);
        row.setInstrumentType(type);
        row.setTradable(true);
        return row;
    }

    private static PriceQuote quote(String symbol, String price, boolean stale) {
        return new PriceQuote(symbol, new BigDecimal(price), null, null, null, null, null, "INR",
                Instant.parse("2026-10-06T04:59:00Z"), stale);
    }

    private void known(TradableInstrumentRow... rows) {
        when(instruments.findBySymbols(anyList())).thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("prices a stock from Fauxnance and a fund from the NAV service, in one call each")
    void routesBySource() {
        known(row("MRF.NS", "STOCK"), row("122639", "MF"));
        when(fauxnance.quotes(List.of("MRF.NS"))).thenReturn(Map.of("MRF.NS", quote("MRF.NS", "123525", false)));
        when(nav.navs(List.of("122639"))).thenReturn(Map.of("122639", quote("122639", "88.76", false)));

        Map<String, PriceQuote> latest = prices.latest(List.of("MRF.NS", "122639"));

        assertThat(latest).containsOnlyKeys("MRF.NS", "122639");
        assertThat(latest.get("122639").price()).isEqualByComparingTo("88.76");
    }

    @Test
    @DisplayName("thirty stocks cost two requests: 25 and 5")
    void batchesOfTwentyFive() {
        List<String> symbols = IntStream.range(0, 30).mapToObj(i -> "S" + i + ".NS").toList();
        known(symbols.stream().map(s -> row(s, "STOCK")).toArray(TradableInstrumentRow[]::new));
        when(fauxnance.quotes(anyList())).thenReturn(Map.of());

        prices.latest(symbols);

        verify(fauxnance).quotes(symbols.subList(0, 25));
        verify(fauxnance).quotes(symbols.subList(25, 30));
    }

    @Test
    @DisplayName("a stock price is reused for a minute, then asked for again")
    void stockCache() {
        known(row("MRF.NS", "STOCK"));
        when(fauxnance.quotes(List.of("MRF.NS"))).thenReturn(Map.of("MRF.NS", quote("MRF.NS", "123525", false)));

        prices.latest(List.of("MRF.NS"));
        clock.advance(Duration.ofSeconds(59));
        prices.latest(List.of("MRF.NS"));
        verify(fauxnance, times(1)).quotes(anyList());

        clock.advance(Duration.ofSeconds(2));
        prices.latest(List.of("MRF.NS"));
        verify(fauxnance, times(2)).quotes(anyList());
    }

    @Test
    @DisplayName("a NAV is reused for half an hour: it changes once a day")
    void navCache() {
        known(row("122639", "MF"));
        when(nav.navs(List.of("122639"))).thenReturn(Map.of("122639", quote("122639", "88.76", false)));

        prices.latest(List.of("122639"));
        clock.advance(Duration.ofMinutes(29));
        prices.latest(List.of("122639"));

        verify(nav, times(1)).navs(anyList());
    }

    @Test
    @DisplayName("Fauxnance down: the last price is served, marked stale, rather than nothing")
    void upstreamDownServesLastKnown() {
        known(row("MRF.NS", "STOCK"));
        when(fauxnance.quotes(List.of("MRF.NS")))
                .thenReturn(Map.of("MRF.NS", quote("MRF.NS", "123525", false)))
                .thenThrow(new PricingUnavailableException("down"));

        prices.latest(List.of("MRF.NS"));
        clock.advance(Duration.ofMinutes(5));
        PriceQuote served = prices.latest(List.of("MRF.NS")).get("MRF.NS");

        assertThat(served.price()).isEqualByComparingTo("123525");
        assertThat(served.stale()).isTrue();
    }

    @Test
    @DisplayName("Fauxnance down and nothing remembered: the symbol is simply unpriced")
    void upstreamDownNothingKnown() {
        known(row("MRF.NS", "STOCK"));
        when(fauxnance.quotes(anyList())).thenThrow(new PricingUnavailableException("down"));

        assertThat(prices.latest(List.of("MRF.NS"))).isEmpty();
    }

    @Test
    @DisplayName("the day's budget spent: no request, the last price served stale")
    void budgetSpent() {
        prices = service("a-key", 1);
        known(row("MRF.NS", "STOCK"));
        when(fauxnance.quotes(List.of("MRF.NS"))).thenReturn(Map.of("MRF.NS", quote("MRF.NS", "123525", false)));

        prices.latest(List.of("MRF.NS"));
        clock.advance(Duration.ofMinutes(5));
        PriceQuote served = prices.latest(List.of("MRF.NS")).get("MRF.NS");

        verify(fauxnance, times(1)).quotes(anyList());
        assertThat(served.stale()).isTrue();
    }

    @Test
    @DisplayName("a symbol we do not list is never sent upstream")
    void unknownSymbols() {
        known();

        assertThat(prices.latest(List.of("NOPE.NS"))).isEmpty();

        verify(fauxnance, never()).quotes(anyList());
        verify(nav, never()).navs(anyList());
    }

    @Test
    @DisplayName("no Fauxnance key: stocks go unpriced without a request; funds still price")
    void noKey() {
        prices = service("", 400);
        known(row("MRF.NS", "STOCK"), row("122639", "MF"));
        when(nav.navs(List.of("122639"))).thenReturn(Map.of("122639", quote("122639", "88.76", false)));

        assertThat(prices.latest(List.of("MRF.NS", "122639"))).containsOnlyKeys("122639");

        verify(fauxnance, never()).quotes(anyList());
    }
}
