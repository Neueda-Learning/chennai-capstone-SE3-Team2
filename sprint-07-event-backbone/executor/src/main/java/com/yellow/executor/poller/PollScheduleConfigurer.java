package com.yellow.executor.poller;

import com.yellow.executor.config.PollProperties;
import com.yellow.executor.persistence.ExecutionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;
import java.time.Instant;

/**
 * Registers the poll on a schedule whose interval is COMPUTED, not annotated.
 *
 * <p>The obvious implementation is {@code @Scheduled(fixedDelayString =
 * "${POLL_INTERVAL_SECONDS}")}. It is not used, and the reason is the story:
 * the interval floor has to be enforced in code rather than documented and
 * hoped for. An annotation takes whatever the environment says, including a
 * value that spends the whole day's quota before lunch, and there is nowhere to
 * put the arithmetic that would have caught it.
 *
 * <p>A {@link Trigger} asks {@link PollSchedule} after every run instead, so
 * the interval tracks the symbol count as it changes. Someone opening a
 * position in a twenty-sixth instrument turns one request a cycle into two, and
 * the schedule slows down by itself rather than quietly doubling the spend.
 *
 * <p>The scheduler is its own single thread, separate from the Kafka listener
 * container. A poll that blocks on a slow HTTP call must never be able to delay
 * an order a customer is waiting on -- they share a credential and a budget,
 * not a thread.
 */
@Configuration
public class PollScheduleConfigurer implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(PollScheduleConfigurer.class);

    private final MarketDataPoller poller;
    private final PollSchedule schedule;
    private final PollProperties poll;
    private final ExecutionMapper mapper;

    public PollScheduleConfigurer(MarketDataPoller poller,
                                  PollSchedule schedule,
                                  PollProperties poll,
                                  ExecutionMapper mapper) {
        this.poller = poller;
        this.schedule = schedule;
        this.poll = poll;
        this.mapper = mapper;
    }

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler pollScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("market-poller-");

        // DAEMON, so this thread can never be the reason a JVM refuses to exit.
        // A scheduler thread is not work anybody is waiting on: if the process
        // is going down, the poll can go with it and the next start picks up
        // wherever the symbol set is then. Left non-daemon it held a Surefire
        // fork open past System.exit and the runner killed it after thirty
        // seconds.
        scheduler.setDaemon(true);

        // Still let a poll in flight finish on an ORDERLY shutdown, rather than
        // tearing the HTTP call down half way and leaving the quota counter
        // disagreeing with what Fauxnance actually recorded. Short, because
        // nothing downstream is waiting for a quote.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (!poll.enabled()) {
            log.info("market-data poller disabled by configuration; no quota will be spent on it");
            return;
        }

        registrar.setTaskScheduler(pollScheduler());
        registrar.addTriggerTask(poller::pollOnce, nextPoll());

        log.info("market-data poller scheduled: requested interval {}s, floor {}s, batch {}",
                poll.intervalSeconds(), poll.floorSeconds(), poll.batchSize());
    }

    /**
     * The delay before the next poll, recomputed each time from the symbol
     * count as it now is.
     */
    private Trigger nextPoll() {
        return (TriggerContext context) -> {
            Duration interval = intervalNow();

            Instant last = context.lastCompletion();
            // First run waits a full interval rather than firing at startup:
            // the consumer is still joining its group and the database pool is
            // still filling, and a poll competing with that just makes both
            // slower.
            return (last == null ? Instant.now() : last).plus(interval);
        };
    }

    private Duration intervalNow() {
        try {
            return schedule.intervalFor(mapper.findSymbolsWorthPolling().size());
        } catch (Exception e) {
            // The database being briefly unreachable must not kill the
            // schedule. Fall back to the configured interval and try again.
            log.warn("could not read the symbol set to size the next poll; "
                    + "falling back to the configured interval", e);
            return Duration.ofSeconds(Math.max(poll.intervalSeconds(), poll.floorSeconds()));
        }
    }
}
