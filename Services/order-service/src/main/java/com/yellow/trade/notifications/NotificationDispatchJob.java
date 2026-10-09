package com.yellow.trade.notifications;

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
 * Runs the dispatcher on a thread of its own, every couple of seconds. Not on
 * the shared @Scheduled pool: a slow mail server must never delay a KYC
 * decision or a deposit, and this module adds no setting the others share.
 */
@Component
@ConditionalOnProperty(prefix = "notifications.dispatch", name = "enabled", havingValue = "true", matchIfMissing = true)
class NotificationDispatchJob implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchJob.class);

    private final NotificationDispatcher dispatcher;
    private final long intervalMs;
    private ScheduledExecutorService executor;

    NotificationDispatchJob(NotificationDispatcher dispatcher,
                            @Value("${notifications.dispatch.interval-ms:2000}") long intervalMs) {
        this.dispatcher = dispatcher;
        this.intervalMs = intervalMs;
    }

    void runOnce() {
        try {
            dispatcher.dispatchOnce();
        } catch (RuntimeException e) {
            // The database is the likely cause; the rows stay QUEUED for the next pass.
            log.warn("notification dispatch pass failed: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public synchronized void start() {
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "notification-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::runOnce, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }
}
