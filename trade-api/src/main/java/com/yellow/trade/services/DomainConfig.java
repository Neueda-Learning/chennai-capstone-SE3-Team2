package com.yellow.trade.services;

import com.yellow.repositories.AccountRepository;
import com.yellow.repositories.InstrumentRepository;
import com.yellow.repositories.OrderRepository;
import com.yellow.repositories.PositionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the domain into Spring without the domain knowing Spring exists.
 *
 * The domain's OrderService is a plain class with a constructor: no
 * annotation, no component scan, nothing that would put a framework type
 * inside com.yellow.services. That is the constraint the Sprint 5 build
 * enforced with the enforcer plugin and that this project now holds by review.
 * Declaring the bean here is what keeps it true.
 *
 * The four repositories it receives are the MyBatis adapters in
 * com.yellow.trade.persistence. The domain declared the ports; the transport
 * layer supplies the implementations. That is the whole reason the same rules
 * run against a hash map in Sprint 5's tests and against Postgres here.
 */
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
