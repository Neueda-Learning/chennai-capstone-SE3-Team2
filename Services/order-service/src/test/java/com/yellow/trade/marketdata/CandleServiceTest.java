package com.yellow.trade.marketdata;

import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.TradableInstrumentRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandleServiceTest {

    // 05:00 UTC on 6 October is 10:30 in India: the chart ends on the 6th.
    private final PriceServiceTest.MovableClock clock = new PriceServiceTest.MovableClock();
    private final InstrumentMapper instruments = mock(InstrumentMapper.class);
    private final FauxnanceMarketClient fauxnance = mock(FauxnanceMarketClient.class);
    private CandleService candles;

    private static final List<Candle> SERIES = List.of(new Candle(LocalDate.parse("2026-10-05"),
            BigDecimal.ONE, BigDecimal.TWO, BigDecimal.ONE, BigDecimal.TWO, 10L));

    @BeforeEach
    void setUp() {
        candles = service(400);
    }

    private CandleService service(int budget) {
        MarketDataProperties properties = MarketDataPropertiesFixture.budget("http://f", "k", "http://n", "k", budget);
        return new CandleService(instruments, fauxnance, new FauxnanceBudget(properties, clock), properties, clock);
    }

    private void known(String symbol, String type) {
        TradableInstrumentRow row = new TradableInstrumentRow();
        row.setSymbol(symbol);
        row.setInstrumentType(type);
        when(instruments.findBySymbols(List.of(symbol))).thenReturn(List.of(row));
    }

    @Test
    @DisplayName("each range reaches back from today's date in India")
    void ranges() {
        known("MRF.NS", "STOCK");
        when(fauxnance.candles(anyString(), any(), any())).thenReturn(SERIES);

        candles.candles("MRF.NS", ChartRange.ONE_MONTH);
        candles.candles("MRF.NS", ChartRange.SIX_MONTHS);
        candles.candles("MRF.NS", ChartRange.FIVE_YEARS);

        LocalDate today = LocalDate.parse("2026-10-06");
        verify(fauxnance).candles("MRF.NS", LocalDate.parse("2026-09-06"), today);
        verify(fauxnance).candles("MRF.NS", LocalDate.parse("2026-04-06"), today);
        verify(fauxnance).candles("MRF.NS", LocalDate.parse("2021-10-06"), today);
    }

    @Test
    @DisplayName("a chart is reused for six hours: daily candles do not move inside a day")
    void cached() {
        known("MRF.NS", "STOCK");
        when(fauxnance.candles(anyString(), any(), any())).thenReturn(SERIES);

        candles.candles("MRF.NS", ChartRange.SIX_MONTHS);
        clock.advance(Duration.ofHours(5));
        assertThat(candles.candles("MRF.NS", ChartRange.SIX_MONTHS)).isEqualTo(SERIES);
        verify(fauxnance, times(1)).candles(anyString(), any(), any());

        clock.advance(Duration.ofHours(2));
        candles.candles("MRF.NS", ChartRange.SIX_MONTHS);
        verify(fauxnance, times(2)).candles(anyString(), any(), any());
    }

    @Test
    @DisplayName("an unknown symbol is INS-404, and Fauxnance is never asked")
    void unknown() {
        when(instruments.findBySymbols(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> candles.candles("NOPE.NS", ChartRange.ONE_YEAR))
                .isInstanceOf(InstrumentNotFoundException.class);
        verify(fauxnance, never()).candles(anyString(), any(), any());
    }

    @Test
    @DisplayName("a fund has no chart yet: refused, and Fauxnance is never asked")
    void fund() {
        known("122639", "MF");

        assertThatThrownBy(() -> candles.candles("122639", ChartRange.ONE_YEAR))
                .isInstanceOf(ChartUnavailableException.class);
        verify(fauxnance, never()).candles(anyString(), any(), any());
    }

    @Test
    @DisplayName("Fauxnance down: an old chart is better than none; with none, pricing unavailable")
    void upstreamDown() {
        known("MRF.NS", "STOCK");
        when(fauxnance.candles(anyString(), any(), any()))
                .thenReturn(SERIES)
                .thenThrow(new PricingUnavailableException("down"));

        candles.candles("MRF.NS", ChartRange.ONE_MONTH);
        clock.advance(Duration.ofHours(7));
        assertThat(candles.candles("MRF.NS", ChartRange.ONE_MONTH)).isEqualTo(SERIES);

        assertThatThrownBy(() -> candles.candles("MRF.NS", ChartRange.ONE_YEAR))
                .isInstanceOf(PricingUnavailableException.class);
    }

    @Test
    @DisplayName("the day's budget spent and nothing remembered: pricing unavailable, no request")
    void budgetSpent() {
        candles = service(0);
        known("MRF.NS", "STOCK");

        assertThatThrownBy(() -> candles.candles("MRF.NS", ChartRange.ONE_MONTH))
                .isInstanceOf(PricingUnavailableException.class);
        verify(fauxnance, never()).candles(anyString(), any(), any());
    }
}
