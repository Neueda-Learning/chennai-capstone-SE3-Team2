package com.yellow.executor.poller;

import com.yellow.executor.config.FauxnanceProperties;
import com.yellow.executor.config.PollProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The quota arithmetic, which the brief says is the assessed part of story 614
 * rather than the code.
 */
class PollScheduleTest {

    @Nested
    @DisplayName("requests per cycle: the batch endpoint's cap is 25")
    class CallsPerCycle {

        @Test
        @DisplayName("25 symbols is one request; 26 is two")
        void batchingIsByTwentyFive() {
            PollSchedule schedule = schedule(60);

            assertThat(schedule.callsPerCycle(1), is(1));
            assertThat(schedule.callsPerCycle(25), is(1));
            // The boundary that changes the arithmetic, and the one a fixed
            // floor of 60 would sail straight past.
            assertThat(schedule.callsPerCycle(26), is(2));
            assertThat(schedule.callsPerCycle(50), is(2));
            assertThat(schedule.callsPerCycle(51), is(3));
        }

        @Test
        @DisplayName("no symbols, no requests")
        void nothingToPollCostsNothing() {
            assertThat(schedule(60).callsPerCycle(0), is(0));
        }
    }

    @Nested
    @DisplayName("what an interval actually spends in a day")
    class Spend {

        @Test
        @DisplayName("60s with 25 symbols is 1440 requests, leaving 560 of 2000 for fills")
        void sixtySecondsFitsTheDay() {
            PollSchedule schedule = schedule(60);

            assertThat(schedule.callsPerDay(25, 60), is(1440L));
            // 2000 - 1440 = 560 for the fill path, on top of the 200 reserve.
            assertThat(schedule.callsPerDay(25, 60), lessThanOrEqualTo(1800L));
        }

        @Test
        @DisplayName("30s is 2880 and the key is gone by mid-afternoon")
        void thirtySecondsDoesNotSurviveTheDay() {
            assertThat(schedule(30).callsPerDay(25, 30), is(2880L));
        }

        @Test
        @DisplayName("15s is 5760: gone before lunch")
        void fifteenSecondsIsHopeless() {
            assertThat(schedule(15).callsPerDay(25, 15), is(5760L));
        }

        @Test
        @DisplayName("eight symbols fetched one at a time every 30s would be 23040")
        void unbatchedIsTheRealDisaster() {
            // The brief's own figure. Batch size 1 is not a configuration this
            // platform allows; it is here to show what batching buys.
            PollSchedule unbatched = new PollSchedule(
                    new PollProperties(30, 30, 1, true), fauxnance());

            assertThat(unbatched.callsPerDay(8, 30), is(23_040L));
            // The same data, batched, is 2880: one request a cycle, not eight.
            assertThat(schedule(30).callsPerDay(8, 30), is(2880L));
        }
    }

    @Nested
    @DisplayName("the floor, enforced in code")
    class Floor {

        @Test
        @DisplayName("a configured 60s with 25 symbols is left alone: it already fits")
        void aSafeIntervalIsNotTouched() {
            assertThat(schedule(60).intervalFor(25), is(Duration.ofSeconds(60)));
        }

        @Test
        @DisplayName("a configured 30s is raised to the 60s floor")
        void belowTheFloorIsRaised() {
            // 30s would spend 2880 of a 1800 budget. Documented-and-hoped-for
            // is exactly what this prevents.
            assertThat(schedule(30).intervalFor(25), is(Duration.ofSeconds(60)));
        }

        @Test
        @DisplayName("a configured 5s is raised too: the floor is a floor, not a suggestion")
        void farBelowTheFloorIsRaised() {
            assertThat(schedule(5).intervalFor(10), is(Duration.ofSeconds(60)));
        }

        @Test
        @DisplayName("a slower interval than the floor is respected: the floor is a minimum, not a target")
        void aboveTheFloorIsHonoured() {
            assertThat(schedule(120).intervalFor(25), is(Duration.ofSeconds(120)));
        }
    }

    @Nested
    @DisplayName("the floor is computed from the symbol count, not a constant")
    class ComputedFloor {

        @Test
        @DisplayName("26 symbols need 96s, so 60 is NOT enough and is raised")
        void twentySixSymbolsRaiseTheFloorAboveSixty() {
            // 26 symbols is two requests a cycle. 86400 x 2 / 1800 = 96s.
            PollSchedule schedule = schedule(60);

            assertThat(schedule.requiredIntervalSeconds(26), is(96L));
            assertThat(schedule.intervalFor(26), is(Duration.ofSeconds(96)));
        }

        @Test
        @DisplayName("51 symbols need three requests a cycle and 144s")
        void fiftyOneSymbolsNeedThreeRequests() {
            // 86400 x 3 / 1800 = 144.
            assertThat(schedule(60).intervalFor(51), is(Duration.ofSeconds(144)));
        }

        @Test
        @DisplayName("whatever the count, the chosen interval stays inside the budget")
        void everyChoiceFitsTheBudget() {
            PollSchedule schedule = schedule(60);

            for (int symbols = 1; symbols <= 120; symbols++) {
                long interval = schedule.intervalFor(symbols).toSeconds();
                assertThat("symbols=" + symbols + " interval=" + interval,
                        schedule.callsPerDay(symbols, interval), lessThanOrEqualTo(1800L));
            }
        }

        @Test
        @DisplayName("the required interval rounds up: a fraction that fits on paper overspends in practice")
        void requiredIntervalRoundsUp() {
            // 86400 / 1800 is exactly 48 for one call. 43, and 123s would
            // spend 702 requests, which is over.
            PollSchedule awkward = new PollSchedule(
                    new PollProperties(60, 60, 25, true),
                    new FauxnanceProperties("http://x", "k", null, 3, null, null, 900, 200));

            assertThat(awkward.requiredIntervalSeconds(25), is(124L));
            assertThat(awkward.callsPerDay(25, 124), lessThanOrEqualTo(700L));
        }
    }

    @Test
    @DisplayName("a budget consumed entirely by the fill reserve is refused, not divided by zero")
    void noBudgetIsAnError() {
        PollSchedule starved = new PollSchedule(
                new PollProperties(60, 60, 25, true),
                new FauxnanceProperties("http://x", "k", null, 3, null, null, 200, 200));

        assertThrows(IllegalStateException.class, () -> starved.requiredIntervalSeconds(10));
    }

    // ----------------------------------------------------------- fixtures

    private static PollSchedule schedule(long intervalSeconds) {
        return new PollSchedule(
                new PollProperties(intervalSeconds, 60, 25, true), fauxnance());
    }

    /** 2000 a day, 200 held back: the poller's budget is 1800. */
    private static FauxnanceProperties fauxnance() {
        return new FauxnanceProperties("http://x", "k", null, 3, null, null, 2000, 200);
    }
}
