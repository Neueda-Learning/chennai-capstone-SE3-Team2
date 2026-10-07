package com.yellow.trade.advice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * The one methodology, from daily closes: the 20-day simple moving average
 * against the 50-day (the trend), confirmed by Wilder's 14-day RSI.
 *
 * - fewer than 50 closes: HOLD, there is no 50-day average yet;
 * - the averages within 0.25% of each other: HOLD, no trend to follow;
 * - the 20-day above: BUY, unless RSI is 70 or more (overbought): HOLD;
 * - the 20-day below: SELL, unless RSI is 30 or less (oversold): HOLD.
 *
 * Strength, 0 to 100: 60 for how far apart the averages are (4% or more is
 * all of it) and 40 for how far RSI agrees past 50 (20 points is all of it).
 * A HOLD has none.
 */
final class Methodology {

    static final String NAME = "20/50-day moving average crossover, confirmed by RSI(14)";
    static final int SHORT = 20;
    static final int LONG = 50;
    static final int RSI_DAYS = 14;
    private static final double LEVEL = 0.25;
    private static final double OVERBOUGHT = 70;
    private static final double OVERSOLD = 30;

    /** The figures and the view they give. Averages and RSI null where there is too little history. */
    record Reading(Direction direction, int strength, String reason, Double sma20, Double sma50, Double rsi14, int days) {
    }

    private Methodology() {
    }

    static Reading read(List<BigDecimal> closes) {
        int days = closes.size();
        Double sma20 = sma(closes, SHORT);
        Double sma50 = sma(closes, LONG);
        Double rsi = rsi14(closes);
        if (sma50 == null || rsi == null) {
            return new Reading(Direction.HOLD, 0, "Only " + days + " days of prices: a 50-day average needs " + LONG
                    + ", so there is no signal yet.", sma20, sma50, rsi, days);
        }
        double gap = (sma20 - sma50) / sma50 * 100;
        String apart = "The 20-day average is " + percent(Math.abs(gap)) + (gap > 0 ? " above" : " below")
                + " the 50-day and RSI is " + Math.round(rsi);
        if (Math.abs(gap) < LEVEL) {
            return new Reading(Direction.HOLD, 0, "The 20-day and 50-day averages are level, within "
                    + percent(Math.abs(gap)) + ", so there is no trend to follow.", sma20, sma50, rsi, days);
        }
        if (gap > 0 && rsi >= OVERBOUGHT) {
            return new Reading(Direction.HOLD, 0, apart + ": the trend is up, but RSI says overbought, so no fresh buy.",
                    sma20, sma50, rsi, days);
        }
        if (gap < 0 && rsi <= OVERSOLD) {
            return new Reading(Direction.HOLD, 0, apart + ": the trend is down, but RSI says oversold, so no fresh sell.",
                    sma20, sma50, rsi, days);
        }
        double agreement = gap > 0 ? (rsi - 50) / 20 : (50 - rsi) / 20;
        int strength = (int) Math.round(100 * (0.6 * Math.min(1, Math.abs(gap) / 4) + 0.4 * clamp(agreement)));
        return new Reading(gap > 0 ? Direction.BUY : Direction.SELL, strength,
                apart + ", so the trend is " + (gap > 0 ? "up." : "down."), sma20, sma50, rsi, days);
    }

    /** The mean of the last n closes; null with fewer than n. */
    static Double sma(List<BigDecimal> closes, int n) {
        if (closes.size() < n) {
            return null;
        }
        return closes.subList(closes.size() - n, closes.size()).stream()
                .mapToDouble(BigDecimal::doubleValue).average().orElseThrow();
    }

    /** Wilder's RSI over 14 days: the first averages plain, every later day smoothed in. Null with 14 closes or fewer. */
    static Double rsi14(List<BigDecimal> closes) {
        if (closes.size() <= RSI_DAYS) {
            return null;
        }
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= RSI_DAYS; i++) {
            double change = closes.get(i).doubleValue() - closes.get(i - 1).doubleValue();
            gain += Math.max(change, 0);
            loss += Math.max(-change, 0);
        }
        gain /= RSI_DAYS;
        loss /= RSI_DAYS;
        for (int i = RSI_DAYS + 1; i < closes.size(); i++) {
            double change = closes.get(i).doubleValue() - closes.get(i - 1).doubleValue();
            gain = (gain * (RSI_DAYS - 1) + Math.max(change, 0)) / RSI_DAYS;
            loss = (loss * (RSI_DAYS - 1) + Math.max(-change, 0)) / RSI_DAYS;
        }
        if (loss == 0) {
            return gain > 0 ? 100.0 : 50.0;
        }
        return 100 - 100 / (1 + gain / loss);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static String percent(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    static BigDecimal figure(Double value, int scale) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    static String lower(Direction direction) {
        return direction.name().toLowerCase(Locale.ROOT);
    }
}
