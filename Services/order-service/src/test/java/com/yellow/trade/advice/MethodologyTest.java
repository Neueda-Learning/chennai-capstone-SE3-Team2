package com.yellow.trade.advice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/** The one methodology: 20- against 50-day moving average, confirmed by RSI(14). Expected figures computed separately. */
class MethodologyTest {

    private static List<BigDecimal> series(int days, java.util.function.IntToDoubleFunction price) {
        return IntStream.range(0, days).mapToObj(i -> BigDecimal.valueOf(price.applyAsDouble(i))).toList();
    }

    /** Rising 0.4 a day, with a swing: the trend up, RSI 60, not overbought. */
    private static final List<BigDecimal> UPTREND = series(80, i -> 100 + 0.4 * i + 4 * Math.sin(i * 1.3));
    private static final List<BigDecimal> DOWNTREND = series(80, i -> 200 - 0.5 * i + 4 * Math.sin(i * 1.3));
    private static final List<BigDecimal> FLAT = series(80, i -> 100 + 4 * Math.sin(i * 1.3));
    /** One per cent every day: up, and RSI 100. */
    private static final List<BigDecimal> STRAIGHT_UP = series(80, i -> 100 * Math.pow(1.01, i));

    @Test
    @DisplayName("an uptrend reads BUY: the 20-day above the 50-day, RSI not overbought")
    void uptrend() {
        Methodology.Reading reading = Methodology.read(UPTREND);

        assertThat(reading.direction(), is(Direction.BUY));
        assertThat(reading.sma20(), closeTo(127.8953, 0.0001));
        assertThat(reading.sma50(), closeTo(121.9153, 0.0001));
        assertThat(reading.rsi14(), closeTo(60.41, 0.01));
        assertThat(reading.strength(), is(81));
        assertThat(reading.reason(), is("The 20-day average is 4.9% above the 50-day and RSI is 60, so the trend is up."));
    }

    @Test
    @DisplayName("a downtrend reads SELL")
    void downtrend() {
        Methodology.Reading reading = Methodology.read(DOWNTREND);

        assertThat(reading.direction(), is(Direction.SELL));
        assertThat(reading.rsi14(), closeTo(45.5, 0.01));
        assertThat(reading.strength(), is(69));
        assertThat(reading.reason(), is("The 20-day average is 4.4% below the 50-day and RSI is 45, so the trend is down."));
    }

    @Test
    @DisplayName("averages level with each other read HOLD: no trend to follow")
    void flat() {
        Methodology.Reading reading = Methodology.read(FLAT);

        assertThat(reading.direction(), is(Direction.HOLD));
        assertThat(reading.strength(), is(0));
        assertThat(reading.reason(), containsString("level"));
    }

    @Test
    @DisplayName("an uptrend with RSI overbought reads HOLD, and says why")
    void overbought() {
        Methodology.Reading reading = Methodology.read(STRAIGHT_UP);

        assertThat(reading.direction(), is(Direction.HOLD));
        assertThat(reading.rsi14(), closeTo(100.0, 0.001));
        assertThat(reading.reason(), containsString("overbought"));
    }

    @Test
    @DisplayName("too little history for a 50-day average is no signal at all, saying so; the figures it has, it gives")
    void tooLittleHistory() {
        Methodology.Reading reading = Methodology.read(UPTREND.subList(0, 32));

        // Not a HOLD: a HOLD is a view, and with 32 days there is none to give.
        assertThat(reading.direction(), is(nullValue()));
        assertThat(reading.strength(), is(nullValue()));
        assertThat(reading.days(), is(32));
        assertThat(reading.sma50(), is(nullValue()));
        assertThat(reading.sma20() == null, is(false));
        assertThat(reading.reason(), is("Only 32 days of prices: a 50-day average needs 50, so there is no signal yet."));
    }

    @Test
    @DisplayName("RSI is Wilder's: smoothed over 14 days")
    void rsi() {
        List<BigDecimal> alternating = series(30, i -> 100 + (i % 2));

        assertThat(Methodology.rsi14(alternating), closeTo(52.46, 0.01));
        assertThat(Methodology.rsi14(alternating.subList(0, 14)), is(nullValue()));
    }
}
