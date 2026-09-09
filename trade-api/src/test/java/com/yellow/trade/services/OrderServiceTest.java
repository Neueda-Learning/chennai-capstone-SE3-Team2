package com.yellow.trade.services;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.entities.Order;
import com.yellow.enums.OrderSide;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.OrderNotFoundException;
import com.yellow.repositories.OrderRepository;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.security.TokenAccountContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private com.yellow.services.OrderService domainOrderService;
    @Mock private InstrumentMapper instrumentMapper;
    @Mock private OrderRepository orderRepository;
    @Mock private TokenAccountContext tokenAccountContext;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(domainOrderService, instrumentMapper, orderRepository, tokenAccountContext);
    }

    private PlaceOrderRequest request() {
        return new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100, new BigDecimal("25.50"), "key-1234");
    }

    @Test
    void placeOrderRefusesWhenRequestAccountDoesNotMatchToken() {
        when(tokenAccountContext.currentAccountId()).thenReturn(99L);

        assertThrows(AccountNotActiveException.class, () -> service.placeOrder(request()));
    }

    @Test
    void placeOrderMapsToOrdPrefixedResponseWhenAccountsMatch() {
        when(tokenAccountContext.currentAccountId()).thenReturn(1L);
        Order order = Order.place(1L, 2L, OrderSide.BUY, new BigDecimal("100"), new BigDecimal("25.50"), "key-1234");
        when(domainOrderService.placeOrder(any(PlaceOrderRequest.class))).thenReturn(order);
        when(instrumentMapper.findSymbolById(2L)).thenReturn("ACME");

        OrderResponse response = service.placeOrder(request());

        assertThat(response.getOrderId(), is(equalTo("ORD-" + order.orderId())));
        assertThat(response.getSymbol(), is(equalTo("ACME")));
        assertThat(response.getMessage(), is(equalTo("Order accepted")));
    }

    @Test
    void cancelOrderThrowsOrderNotFoundWhenOrderDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> service.cancelOrder(orderId));
    }

    @Test
    void cancelOrderRefusesWhenExistingOrderBelongsToAnotherAccount() {
        UUID orderId = UUID.randomUUID();
        Order existing = Order.place(5L, 2L, OrderSide.BUY, new BigDecimal("10"), new BigDecimal("100.00"), "key-1");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existing));
        when(tokenAccountContext.currentAccountId()).thenReturn(1L); // token is for account 1, order belongs to 5

        assertThrows(AccountNotActiveException.class, () -> service.cancelOrder(orderId));
    }
}