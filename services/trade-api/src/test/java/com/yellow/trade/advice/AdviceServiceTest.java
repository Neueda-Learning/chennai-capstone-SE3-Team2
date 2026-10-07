package com.yellow.trade.advice;

import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.marketdata.Candle;
import com.yellow.trade.marketdata.CandleService;
import com.yellow.trade.marketdata.ChartRange;
import com.yellow.trade.marketdata.PricingUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdviceServiceTest {

    /** 10:00 IST on 7 Oct 2026: the last candle is the 6th. */
    private static final Instant NOW = Instant.parse("2026-10-07T04:30:00Z");

    @Mock private InstrumentMapper instruments;
    @Mock private CandleService candles;

    private final LatestPrices prices = new LatestPrices();
    private final MutableClock clock = new MutableClock(NOW);

    private AdviceService service() {
        return new AdviceService(instruments, candles, prices, clock, 300_000L);
    }

    private void listed(String symbol, String type) {
        InstrumentRow row = new InstrumentRow();
        row.setSymbol(symbol);
        row.setInstrumentType(type);
        row.setTradable(true);
        when(instruments.findBySymbol(symbol)).thenReturn(row);
    }

    /** 80 trading days to the 6th, rising with a swing. */
    private static List<Candle> uptrend() {
        LocalDate last = LocalDate.parse("2026-10-06");
        return IntStream.range(0, 80).mapToObj(i -> {
            BigDecimal close = BigDecimal.valueOf(100 + 0.4 * i + 4 * Math.sin(i * 1.3));
            return new Candle(last.minusDays(79 - i), close, close, close, close, 1000L);
        }).toList();
    }

    @Test
    @DisplayName("a signal from real candles: the methodology, the figures, the sentence, and that it is not advice")
    void signal() {
        listed("ITC.NS", "STOCK");
        when(candles.candles("ITC.NS", ChartRange.SIX_MONTHS)).thenReturn(uptrend());

        Signal signal = service().signal("ITC.NS");

        assertThat(signal.direction(), is(Direction.BUY));
        assertThat(signal.strength(), is(81));
        assertThat(signal.methodology(), is("20/50-day moving average crossover, confirmed by RSI(14)"));
        assertThat(signal.figures().days(), is(80));
        assertThat(signal.figures().sma20(), comparesEqualTo(new BigDecimal("127.8953")));
        assertThat(signal.figures().rsi14(), comparesEqualTo(new BigDecimal("60.41")));
        assertThat(signal.disclaimer(), containsString("not advice"));
        assertThat(signal.computedAt(), is(NOW));
    }

    @Test
    @DisplayName("a fund is refused: it has no daily candles here; nothing is asked of the candle service")
    void fund() {
        listed("122639", "MF");

        assertThrows(AdviceExceptions.FundSignalException.class, () -> service().signal("122639"));
        verifyNoInteractions(candles);
    }

    @Test
    @DisplayName("a symbol nobody lists is INS-404")
    void unknown() {
        when(instruments.findBySymbol("NOPE.NS")).thenReturn(null);

        assertThrows(InstrumentNotFoundException.class, () -> service().signal("NOPE.NS"));
    }

    @Test
    @DisplayName("no candles to be had is MKT-503")
    void noCandles() {
        listed("ITC.NS", "STOCK");
        when(candles.candles(any(), any())).thenThrow(new PricingUnavailableException("no chart"));

        assertThrows(PricingUnavailableException.class, () -> service().signal("ITC.NS"));
    }

    @Test
    @DisplayName("asked again inside the window, the signal is the one computed: candles are not read again")
    void cached() {
        listed("ITC.NS", "STOCK");
        when(candles.candles("ITC.NS", ChartRange.SIX_MONTHS)).thenReturn(uptrend());
        AdviceService service = service();

        service.signal("ITC.NS");
        clock.advance(60_000);
        service.signal("ITC.NS");

        verify(candles, times(1)).candles("ITC.NS", ChartRange.SIX_MONTHS);
    }

    @Test
    @DisplayName("recomputed on the timer, not on every quote: a quote alone changes nothing until the next pass")
    void timer() {
        listed("ITC.NS", "STOCK");
        when(candles.candles("ITC.NS", ChartRange.SIX_MONTHS)).thenReturn(uptrend());
        AdviceService service = service();
        Signal first = service.signal("ITC.NS");

        prices.offer("ITC.NS", new BigDecimal("150.00"), NOW);
        assertThat(service.signal("ITC.NS"), is(first));

        clock.advance(300_000);
        service.refreshAll();
        Signal refreshed = service.signal("ITC.NS");
        assertThat(refreshed.figures().lastPrice(), comparesEqualTo(new BigDecimal("150.00")));
        assertThat(refreshed.figures().days(), is(81));
    }

    @Test
    @DisplayName("today's price from market-data counts as today's close; one no newer than the last candle does not")
    void todaysPrice() {
        listed("ITC.NS", "STOCK");
        when(candles.candles("ITC.NS", ChartRange.SIX_MONTHS)).thenReturn(uptrend());
        prices.offer("ITC.NS", new BigDecimal("140.00"), Instant.parse("2026-10-06T09:00:00Z"));

        Signal signal = service().signal("ITC.NS");

        assertThat(signal.figures().days(), is(80));
        assertThat(signal.figures().lastPrice(), comparesEqualTo(new BigDecimal("140.00")));
    }

    /** A clock a test can move. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
