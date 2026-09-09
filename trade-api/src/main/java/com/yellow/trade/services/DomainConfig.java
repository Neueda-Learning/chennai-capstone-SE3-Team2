package com.yellow.trade.services;

import com.yellow.repositories.AccountRepository;
import com.yellow.repositories.InstrumentRepository;
import com.yellow.repositories.OrderRepository;
import com.yellow.repositories.PositionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// wires the Sprint 5 domain's own OrderService as a Spring bean
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
}