package com.yellow.trade.strategy;

import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyTriggerTest {

    private static final StrategyQuote QUOTE = new StrategyQuote(UUID.fromString("3a5c7e91-2b4d-4f60-8c1e-9d0f2a4b6c8e"), "ITC.NS",
            new BigDecimal("259.50"), new BigDecimal("259.45"), new BigDecimal("259.55"), Instant.parse("2026-10-07T05:00:00Z"));
    private static final Indicators.View VIEW = new Indicators.View(new BigDecimal("259.50"), QUOTE.quoteAsOf(), 80,
            255.0, 256.0, 256.2, 256.1, 240.0, 270.0);

    @Mock private InstrumentMapper instruments;
    @Mock private StrategyMapper strategies;
    @Mock private StrategyFirer firer;
    @Mock private IndicatorReader indicators;

    private StrategyTrigger trigger() {
        InstrumentRow itc = new InstrumentRow();
        itc.setInstrumentId(42L);
        itc.setSymbol("ITC.NS");
        when(instruments.findBySymbol("ITC.NS")).thenReturn(itc);
        return new StrategyTrigger(instruments, strategies, firer, indicators);
    }

    @Test
    @DisplayName("a quote fires the level strategies it crosses, and the indicator ones with a view read once for all of them")
    void both() {
        StrategyTrigger trigger = trigger();
        when(strategies.findCrossed(42L, QUOTE.price())).thenReturn(List.of(5L));
        when(strategies.findArmedIndicators(42L)).thenReturn(List.of(7L, 8L));
        when(indicators.view("ITC.NS", QUOTE)).thenReturn(Optional.of(VIEW));

        trigger.onQuote(QUOTE);

        verify(firer).fire(5L, QUOTE);
        verify(firer).fire(7L, QUOTE, VIEW);
        verify(firer).fire(8L, QUOTE, VIEW);
        verify(indicators, times(1)).view("ITC.NS", QUOTE);
    }

    @Test
    @DisplayName("no indicator strategy on the stock: no candles are asked for")
    void levelOnly() {
        StrategyTrigger trigger = trigger();
        when(strategies.findCrossed(42L, QUOTE.price())).thenReturn(List.of());
        when(strategies.findArmedIndicators(42L)).thenReturn(List.of());

        trigger.onQuote(QUOTE);

        verifyNoInteractions(indicators);
    }

    @Test
    @DisplayName("no view to be had (no candles): the indicator strategies wait for a later quote")
    void noView() {
        StrategyTrigger trigger = trigger();
        when(strategies.findCrossed(42L, QUOTE.price())).thenReturn(List.of());
        when(strategies.findArmedIndicators(42L)).thenReturn(List.of(7L));
        when(indicators.view("ITC.NS", QUOTE)).thenReturn(Optional.empty());

        trigger.onQuote(QUOTE);

        verify(firer, never()).fire(anyLong(), any(), any());
        verify(firer, never()).fire(eq(7L), any());
    }
}
