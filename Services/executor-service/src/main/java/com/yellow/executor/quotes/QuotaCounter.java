package com.yellow.executor.quotes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

/** One place that counts what has been spent, shared by both callers. */
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
     * Whether the poller may make calls more requests without eating into the
     * fill path's reserve. The fill path never asks: it is always allowed to
     * price an order a customer is waiting for.
     */
    public boolean canAfford(int calls, int budget) {
        return spentToday() + calls <= budget;
    }

    /** Reconcile against the API's own count. */
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
