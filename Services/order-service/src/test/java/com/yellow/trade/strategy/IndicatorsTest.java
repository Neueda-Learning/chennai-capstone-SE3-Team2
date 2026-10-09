package com.yellow.trade.strategy;

import com.yellow.trade.marketdata.Candle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class IndicatorsTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-07");
    private static final Instant AT = Instant.parse("2026-10-07T05:00:00Z");

    /** Daily closes to yesterday, oldest first. */
    private static List<Candle> closes(double... prices) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            BigDecimal close = BigDecimal.valueOf(prices[i]);
            candles.add(new Candle(TODAY.minusDays(prices.length - i), close, close, close, close, 1000L));
        }
        return candles;
    }

    /** 30 days at 100, then 20 at 99: at yesterday's close the 20-day (99) is under the 50-day (99.6). */
    private static List<Candle> dipped() {
        double[] prices = new double[50];
        for (int i = 0; i < 50; i++) {
            prices[i] = i < 30 ? 100 : 99;
        }
        return closes(prices);
    }

    /** The mirror: 30 days at 100, then 20 at 101; the 20-day (101) over the 50-day (100.4). */
    private static List<Candle> lifted() {
        double[] prices = new double[50];
        for (int i = 0; i < 50; i++) {
            prices[i] = i < 30 ? 100 : 101;
        }
        return closes(prices);
    }

    private static Indicators.View read(List<Candle> candles, String price) {
        return Indicators.read(candles, new BigDecimal(price), AT, TODAY);
    }

    @Test
    @DisplayName("strategy triggers on a crossover condition: under at yesterday's close, today's price lifts the 20-day over the 50-day")
    void crossesUp() {
        // (19 x 99 + 120) / 20 = 100.05 against (29 x 100 + 20 x 99 + 120) / 50 = 100.0
        Indicators.View view = read(dipped(), "120");

        assertThat(view.shortBefore(), closeTo(99.0, 1e-9));
        assertThat(view.longBefore(), closeTo(99.6, 1e-9));
        assertThat(view.shortNow(), closeTo(100.05, 1e-9));
        assertThat(view.longNow(), closeTo(100.0, 1e-9));
        assertThat(view.crossedUp(), is(true));
        assertThat(view.crossedDown(), is(false));
    }

    @Test
    @DisplayName("no action when the condition is not met: today's price moves the 20-day, but not over the 50-day")
    void noCross() {
        Indicators.View view = read(dipped(), "110");

        assertThat(view.crossedUp(), is(false));
        assertThat(view.crossedDown(), is(false));
    }

    @Test
    @DisplayName("the cross down: over at yesterday's close, today's price takes the 20-day under the 50-day")
    void crossesDown() {
        assertThat(read(lifted(), "80").crossedDown(), is(true));
        assertThat(read(lifted(), "95").crossedDown(), is(false));
    }

    @Test
    @DisplayName("the Bollinger band: 20 days' average and two standard deviations either side, today's price counted")
    void bands() {
        // 19 days alternating 98 and 102, then today at 100: mean 99.9, and the standard deviation over
        // the 20 as Bollinger takes it (divided by 20, not 19) 1.9468
        double[] prices = new double[19];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = i % 2 == 0 ? 98 : 102;
        }
        Indicators.View inside = read(closes(prices), "100");

        assertThat(inside.lowerBand(), closeTo(96.006, 0.001));
        assertThat(inside.upperBand(), closeTo(103.794, 0.001));
        assertThat(inside.atLowerBand(), is(false));
        assertThat(inside.atUpperBand(), is(false));
        assertThat(read(closes(prices), "90").atLowerBand(), is(true));
        assertThat(read(closes(prices), "110").atUpperBand(), is(true));
    }

    @Test
    @DisplayName("today's candle, where there is one, gives way to today's live price")
    void todaysCandle() {
        List<Candle> withToday = new ArrayList<>(dipped());
        withToday.add(new Candle(TODAY, BigDecimal.valueOf(99), BigDecimal.valueOf(99), BigDecimal.valueOf(99), BigDecimal.valueOf(99), 1000L));

        Indicators.View view = read(withToday, "120");

        assertThat(view.days(), is(51));
        assertThat(view.crossedUp(), is(true));
    }

    @Test
    @DisplayName("too little history: no averages, no band, and nothing crosses")
    void tooLittle() {
        Indicators.View view = read(closes(100, 101, 102), "103");

        assertThat(view.longNow(), is(nullValue()));
        assertThat(view.lowerBand(), is(nullValue()));
        assertThat(view.crossedUp(), is(false));
        assertThat(view.atLowerBand(), is(false));
    }
}
