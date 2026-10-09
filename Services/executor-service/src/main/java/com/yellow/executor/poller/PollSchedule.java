package com.yellow.executor.poller;

import com.yellow.executor.config.FauxnanceProperties;
import com.yellow.executor.config.PollProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * How often the poller may actually run. THE FLOOR IS ENFORCED HERE, IN CODE,
 * NOT DOCUMENTED AND HOPED FOR.
 */
@Component
public class PollSchedule {

    private static final Logger log = LoggerFactory.getLogger(PollSchedule.class);

    private static final long SECONDS_PER_DAY = 86_400L;

    private final PollProperties poll;
    private final FauxnanceProperties fauxnance;

    public PollSchedule(PollProperties poll, FauxnanceProperties fauxnance) {
        this.poll = poll;
        this.fauxnance = fauxnance;
    }

    /**
     * The interval to actually use for a cycle covering symbolCount symbols:
     * never below the configured floor, and never fast enough to overspend the
     * poller's share of the daily quota.
     */
    public Duration intervalFor(int symbolCount) {
        long configured = poll.intervalSeconds();
        long floor = poll.floorSeconds();
        long required = requiredIntervalSeconds(symbolCount);

        long effective = Math.max(configured, Math.max(floor, required));

        if (effective > configured) {
            // Say why, with the numbers, rather than silently running slower
            // than the operator asked for.
            log.warn("poll interval raised from {}s to {}s: {} symbols need {} request(s) a cycle, "
                            + "and {}s would spend {} of the poller's {} daily requests",
                    configured, effective, symbolCount, callsPerCycle(symbolCount),
                    configured, callsPerDay(symbolCount, configured), fauxnance.pollerBudget());
        }

        return Duration.ofSeconds(effective);
    }

    /** The fastest interval that keeps this symbol count inside the budget. */
    public long requiredIntervalSeconds(int symbolCount) {
        int budget = fauxnance.pollerBudget();
        if (budget <= 0) {
            // Nothing is left for the poller. Refuse rather than divide by
            // zero and schedule something meaningless.
            throw new IllegalStateException(
                    "the poller has no quota: daily-quota " + fauxnance.dailyQuota()
                            + " minus fill-reserve " + fauxnance.fillReserve() + " leaves nothing");
        }
        long callsPerDay = SECONDS_PER_DAY * callsPerCycle(symbolCount);
        return ceilDiv(callsPerDay, budget);
    }

    /** One request per 25 symbols, or part thereof. Zero symbols, zero calls. */
    public int callsPerCycle(int symbolCount) {
        return symbolCount <= 0 ? 0 : (int) ceilDiv(symbolCount, poll.batchSize());
    }

    /** What a given interval would actually spend in a day. For the logs and the review. */
    public long callsPerDay(int symbolCount, long intervalSeconds) {
        if (intervalSeconds <= 0) {
            return Long.MAX_VALUE;
        }
        return (SECONDS_PER_DAY / intervalSeconds) * callsPerCycle(symbolCount);
    }

    private static long ceilDiv(long dividend, long divisor) {
        return (dividend + divisor - 1) / divisor;
    }
}
