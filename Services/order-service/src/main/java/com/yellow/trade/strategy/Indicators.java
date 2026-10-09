package com.yellow.trade.strategy;

import com.yellow.trade.marketdata.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The two indicator triggers, from daily closes with today's live price counted as today's
 * close (decision log 0017). The 20-day simple moving average against the 50-day: a crossover
 * is the 20-day on one side at yesterday's close and on the other now. The Bollinger band: the
 * 20-day average two standard deviations either side, the deviation over the 20 as Bollinger
 * takes it (divided by 20). Without enough closes there is no average and no band, and nothing
 * fires.
 */
final class Indicators {

    static final int SHORT = 20;
    static final int LONG = 50;
    static final int BAND_DAYS = 20;
    static final double BAND_WIDTH = 2;

    /** One instrument at one quote: every figure either trigger needs. Null where history is too short. */
    record View(BigDecimal price, Instant asOf, int days, Double shortBefore, Double longBefore, Double shortNow,
                Double longNow, Double lowerBand, Double upperBand) {

        boolean crossedUp() {
            return averages() && shortBefore <= longBefore && shortNow > longNow;
        }

        boolean crossedDown() {
            return averages() && shortBefore >= longBefore && shortNow < longNow;
        }

        boolean atLowerBand() {
            return lowerBand != null && price.doubleValue() <= lowerBand;
        }

        boolean atUpperBand() {
            return upperBand != null && price.doubleValue() >= upperBand;
        }

        private boolean averages() {
            return shortBefore != null && longBefore != null && shortNow != null && longNow != null;
        }
    }

    private Indicators() {
    }

    /** The view at a quote on {@code today}: any candle for today gives way to the live price. */
    static View read(List<Candle> daily, BigDecimal price, Instant asOf, LocalDate today) {
        List<BigDecimal> before = daily.stream()
                .filter(candle -> candle.date().isBefore(today))
                .sorted(Comparator.comparing(Candle::date))
                .map(Candle::close)
                .toList();
        List<BigDecimal> now = new ArrayList<>(before);
        now.add(price);
        Double lower = null;
        Double upper = null;
        if (now.size() >= BAND_DAYS) {
            double mean = sma(now, BAND_DAYS);
            double variance = now.subList(now.size() - BAND_DAYS, now.size()).stream()
                    .mapToDouble(close -> Math.pow(close.doubleValue() - mean, 2)).sum() / BAND_DAYS;
            double width = BAND_WIDTH * Math.sqrt(variance);
            lower = mean - width;
            upper = mean + width;
        }
        return new View(price, asOf, now.size(), sma(before, SHORT), sma(before, LONG), sma(now, SHORT), sma(now, LONG),
                lower, upper);
    }

    /** The mean of the last n closes; null with fewer than n. */
    static Double sma(List<BigDecimal> closes, int n) {
        if (closes.size() < n) {
            return null;
        }
        return closes.subList(closes.size() - n, closes.size()).stream()
                .mapToDouble(BigDecimal::doubleValue).average().orElseThrow();
    }

    /** What fired an indicator strategy, for its run: the figures that crossed. */
    static String why(Trigger trigger, boolean buy, View view) {
        return switch (trigger) {
            case MA_CROSSOVER -> "The 20-day average (" + money(view.shortNow()) + ") crossed " + (buy ? "above" : "below")
                    + " the 50-day (" + money(view.longNow()) + ").";
            case BOLLINGER -> "The price (" + money(view.price().doubleValue()) + ") reached the " + (buy ? "lower" : "upper")
                    + " Bollinger band (" + money(buy ? view.lowerBand() : view.upperBand()) + ").";
            default -> null;
        };
    }

    static BigDecimal scaled(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String money(Double value) {
        return scaled(value).toPlainString();
    }
}
