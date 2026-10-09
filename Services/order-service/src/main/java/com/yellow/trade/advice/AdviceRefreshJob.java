package com.yellow.trade.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Recomputes the signals on a timer, every five minutes, on a thread of its
 * own: not on every quote, which would be cost and noise, and not on the
 * shared @Scheduled pool, so nothing here delays a KYC decision or a deposit.
 */
@Component
@ConditionalOnProperty(prefix = "advice.refresh", name = "enabled", havingValue = "true", matchIfMissing = true)
class AdviceRefreshJob implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AdviceRefreshJob.class);

    private final AdviceService advice;
    private final long intervalMs;
    private ScheduledExecutorService executor;

    AdviceRefreshJob(AdviceService advice, @Value("${advice.refresh.interval-ms:300000}") long intervalMs) {
        this.advice = advice;
        this.intervalMs = intervalMs;
    }

    @Override
    public synchronized void start() {
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "advice-refresh");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                advice.refreshAll();
            } catch (RuntimeException e) {
                log.warn("advice refresh pass failed: {}", e.getClass().getSimpleName());
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }
}
