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
 * The obvious implementation is @Scheduled(fixedDelayString =
 * "${POLL_INTERVAL_SECONDS")}.
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

        // DAEMON, so this thread can never be the reason a JVM refuses to
        // exit.
        scheduler.setDaemon(true);

        // Still let a poll in flight finish on an ORDERLY shutdown, rather
        // than tearing the HTTP call down half way and leaving the quota
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

    /** The delay before the next poll, recomputed each time from the symbol count as it now is. */
    private Trigger nextPoll() {
        return (TriggerContext context) -> {
            Duration interval = intervalNow();

            Instant last = context.lastCompletion();
            // First run waits a full interval rather than firing at startup:
            // the consumer is still joining its group and the database pool
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
