package com.yellow.executor.quotes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One place that counts what has been spent, shared by both callers.
 *
 * <p>The executor calls Fauxnance from two directions: the fill path prices
 * every order, and the poller fetches a batch on a schedule. They share one key
 * and therefore one allowance of 2000 requests a day. Two counters would be two
 * opinions about the same number, and the first symptom of getting it wrong is
 * orders rejected because no price could be obtained -- which reads like a
 * Fauxnance outage and is actually the poller having eaten the budget.
 *
 * <p>So: one counter, and the poller stops at a boundary that leaves the fill
 * path a reserve. An order the customer is waiting on outranks a price update
 * nobody asked for.
 *
 * <p>The quota resets at 00:00 UTC, which is not local midnight for this team
 * -- it is 05:30 IST. A session running late in the evening IST is spending the
 * same day's budget as one that started that morning.
 */
@Component
public class QuotaCounter {

    private static final Logger log = LoggerFactory.getLogger(QuotaCounter.class);

    private final Clock clock;
    private final AtomicInteger spent = new AtomicInteger();
    private volatile LocalDate window;

    public QuotaCounter(Clock clock) {
        this.clock = clock;
        this.window = today();
    }

    /** Record one request, whether it succeeded or not. A 429 costs a call too. */
    public int spend() {
        rollIfNewDay();
        return spent.incrementAndGet();
    }

    public int spentToday() {
        rollIfNewDay();
        return spent.get();
    }

    /**
     * Whether the poller may make {@code calls} more requests without eating
     * into the fill path's reserve. The fill path never asks: it is always
     * allowed to price an order a customer is waiting for.
     */
    public boolean canAfford(int calls, int budget) {
        return spentToday() + calls <= budget;
    }

    /**
     * Reconcile against the API's own count.
     *
     * <p>GET /usage costs a quota unit itself, so this is called on startup and
     * occasionally -- never once per poll cycle, which would double the
     * poller's spend to learn what we are already tracking.
     */
    public void reconcile(int usedToday) {
        rollIfNewDay();
        int local = spent.getAndSet(usedToday);
        if (local != usedToday) {
            log.info("quota reconciled: counted {} locally, API says {}", local, usedToday);
        }
    }

    private void rollIfNewDay() {
        LocalDate now = today();
        if (!now.equals(window)) {
            synchronized (this) {
                if (!now.equals(window)) {
                    log.info("quota window rolled to {}; {} requests were spent yesterday",
                            now, spent.get());
                    spent.set(0);
                    window = now;
                }
            }
        }
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
