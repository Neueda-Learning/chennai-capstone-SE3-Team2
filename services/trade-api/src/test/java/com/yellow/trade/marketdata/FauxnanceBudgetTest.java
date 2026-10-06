package com.yellow.trade.marketdata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FauxnanceBudgetTest {

    private final PriceServiceTest.MovableClock clock = new PriceServiceTest.MovableClock();

    private FauxnanceBudget budget(int daily) {
        return new FauxnanceBudget(MarketDataPropertiesFixture.budget("http://f", "k", "http://n", "k", daily), clock);
    }

    @Test
    @DisplayName("allows the day's budget and refuses the next request")
    void refusesPastTheBudget() {
        FauxnanceBudget budget = budget(2);

        assertThat(budget.tryTake()).isTrue();
        assertThat(budget.tryTake()).isTrue();
        assertThat(budget.tryTake()).isFalse();
        assertThat(budget.remaining()).isZero();
    }

    @Test
    @DisplayName("starts again at midnight UTC, when Fauxnance resets its own count")
    void resetsAtMidnightUtc() {
        FauxnanceBudget budget = budget(1);
        budget.tryTake();

        clock.advance(Duration.ofHours(18)); // 23:00 UTC, still the same Fauxnance day
        assertThat(budget.tryTake()).isFalse();

        clock.advance(Duration.ofHours(1)); // 00:00 UTC
        assertThat(budget.tryTake()).isTrue();
    }
}
