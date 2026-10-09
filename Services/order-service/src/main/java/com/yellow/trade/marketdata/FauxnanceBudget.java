package com.yellow.trade.marketdata;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * This service's share of the day's Fauxnance requests, counted in the same
 * day Fauxnance counts in: the UTC day. Spent, it refuses, and callers serve
 * what they already have.
 */
@Component
public class FauxnanceBudget {

    private final int daily;
    private final Clock clock;
    private LocalDate day;
    private int spent;

    public FauxnanceBudget(MarketDataProperties properties, Clock clock) {
        this.daily = properties.fauxnanceDailyBudget();
        this.clock = clock;
    }

    public synchronized boolean tryTake() {
        rollOver();
        if (spent >= daily) {
            return false;
        }
        spent++;
        return true;
    }

    public synchronized int remaining() {
        rollOver();
        return daily - spent;
    }

    private void rollOver() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!today.equals(day)) {
            day = today;
            spent = 0;
        }
    }
}
