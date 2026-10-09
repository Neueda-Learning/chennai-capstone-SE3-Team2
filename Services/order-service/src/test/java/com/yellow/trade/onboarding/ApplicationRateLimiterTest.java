package com.yellow.trade.onboarding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ApplicationRateLimiterTest {

    /** A clock the test moves by hand. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T05:30:00Z");
        void advance(java.time.Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MovableClock clock = new MovableClock();
    private final ApplicationRateLimiter limiter = new ApplicationRateLimiter(clock);

    @Test
    @DisplayName("Five applications from one caller are allowed and the sixth is refused")
    void sixthInTheWindowIsRefused() {
        for (int i = 1; i <= ApplicationRateLimiter.LIMIT; i++) {
            assertThat("attempt " + i, limiter.tryAcquire("10.0.0.1"), is(true));
        }
        assertThat(limiter.tryAcquire("10.0.0.1"), is(false));
    }

    @Test
    @DisplayName("One caller's limit does not touch another's")
    void callersAreCountedSeparately() {
        for (int i = 0; i < ApplicationRateLimiter.LIMIT; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        assertThat(limiter.tryAcquire("10.0.0.1"), is(false));
        assertThat(limiter.tryAcquire("10.0.0.2"), is(true));
    }

    @Test
    @DisplayName("The count starts again once the hour has passed")
    void windowExpiryResetsTheCount() {
        for (int i = 0; i <= ApplicationRateLimiter.LIMIT; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        assertThat(limiter.tryAcquire("10.0.0.1"), is(false));

        clock.advance(ApplicationRateLimiter.WINDOW);

        assertThat(limiter.tryAcquire("10.0.0.1"), is(true));
    }
}
