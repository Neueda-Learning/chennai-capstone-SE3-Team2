package com.yellow.executor;

import com.yellow.executor.config.FauxnanceProperties;
import com.yellow.executor.config.PollProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** The Trade Executor. Two jobs in one process, on purpose. */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({FauxnanceProperties.class, PollProperties.class})
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
