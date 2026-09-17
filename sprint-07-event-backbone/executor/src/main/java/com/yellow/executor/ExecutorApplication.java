package com.yellow.executor;

import com.yellow.executor.config.FauxnanceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * The Trade Executor.
 *
 * <p>Two jobs in one process, on purpose. It consumes accepted orders, prices
 * them and settles them; and it hosts the market-data poller. The poller lives
 * here because of the key: the executor already calls Fauxnance to price every
 * fill, and a separate poller would mean a second process holding the same
 * credential, spending the same 2000 requests with no idea what the other one
 * had spent, and an argument about which of the two owned the retry policy. One
 * component calls Fauxnance, so one component holds the key and one component
 * divides the budget.
 *
 * <p>Sharing a process is not sharing a lifecycle. The poller is not on the
 * order path: it does not start a poll because an order arrived, and the
 * consumer never waits for one to finish.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(FauxnanceProperties.class)
public class ExecutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutorApplication.class, args);
    }

    /**
     * Injected rather than called statically, so a test can fix "now" and
     * assert on a timestamp instead of asserting that one is roughly present.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
