package com.yellow.executor.poller;

import com.yellow.executor.config.FauxnanceProperties;
import com.yellow.executor.config.PollProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * How often the poller may actually run.
 *
 * <p>THE FLOOR IS ENFORCED HERE, IN CODE, NOT DOCUMENTED AND HOPED FOR. That
 * distinction is the assessed part of story 614, and it is why this is a class
 * with tests rather than a number in a YAML file: a configured interval that
 * would overspend the daily quota is raised, and the arithmetic that raised it
 * is logged so nobody has to guess why the poller is slower than they asked
 * for.
 *
 * <h2>The arithmetic</h2>
 *
 * <pre>
 *   callsPerCycle = ceil(symbols / 25)        the batch endpoint's hard cap
 *   callsPerDay   = (86400 / interval) x callsPerCycle
 *   required:       callsPerDay &lt;= budget
 *   therefore:      interval &gt;= 86400 x callsPerCycle / budget
 * </pre>
 *
 * <p>With a budget of 1800 -- 2000 a day less the 200 held back for the fill
 * path -- and 25 symbols or fewer, that is 48 seconds. The configured floor of
 * 60 is the binding constraint there, and 60 seconds costs 1440 requests a day
 * and leaves 560 for orders.
 *
 * <p>THE FLOOR IS NOT A CONSTANT, and this is the part teams get wrong. At 26
 * symbols the batch becomes two calls a cycle and the arithmetic demands 96
 * seconds; a fixed floor of 60 would quietly spend 2880 requests and lose the
 * key by mid-afternoon. The symbol count is read every cycle, so a poller that
 * was inside its budget yesterday does not fall outside it because somebody
 * opened a position in a twenty-sixth instrument.
 *
 * <p>For scale: eight symbols fetched ONE AT A TIME every 30 seconds is 23040
 * requests and the key is gone in two hours. The same data batched is 2880.
 * Batching is what makes the poller possible at all.
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
     * The interval to actually use for a cycle covering {@code symbolCount}
     * symbols: never below the configured floor, and never fast enough to
     * overspend the poller's share of the daily quota.
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

    /**
     * The fastest interval that keeps this symbol count inside the budget.
     *
     * <p>Rounded UP, because a fractional second that satisfies the inequality
     * on paper spends one request too many once the scheduler rounds it down.
     */
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
