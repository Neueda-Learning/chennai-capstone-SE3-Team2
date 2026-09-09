package com.yellow.trade.services;

import com.yellow.repositories.AccountRepository;
import com.yellow.repositories.InstrumentRepository;
import com.yellow.repositories.OrderRepository;
import com.yellow.repositories.PositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(MockitoExtension.class)
class DomainConfigTest {

    @Mock private AccountRepository accountRepository;
    @Mock private InstrumentRepository instrumentRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private OrderRepository orderRepository;

    @Test
    void domainOrderServiceBeanIsConstructedFromTheFourRepositories() {
        DomainConfig config = new DomainConfig();

        com.yellow.services.OrderService bean = config.domainOrderService(
                accountRepository, instrumentRepository, positionRepository, orderRepository);

        assertThat(bean, is(notNullValue()));
    }
}