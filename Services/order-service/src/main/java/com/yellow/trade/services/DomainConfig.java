package com.yellow.trade.services;

import com.yellow.repositories.AccountRepository;
import com.yellow.repositories.InstrumentRepository;
import com.yellow.repositories.OrderRepository;
import com.yellow.repositories.PositionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DomainConfig {

    @Bean
    public com.yellow.services.OrderService domainOrderService(
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            PositionRepository positionRepository,
            OrderRepository orderRepository) {

        return new com.yellow.services.OrderService(
                accountRepository, instrumentRepository, positionRepository, orderRepository);
    }

    /**
     * Injected rather than called statically so that a test can fix "now" and
     * assert on a timestamp instead of asserting that one is roughly present.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
