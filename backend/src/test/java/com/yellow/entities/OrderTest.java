package com.yellow.entities;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    @Test
    @DisplayName("Should create a NEW order with no resolved time and no executed price")
    void shouldCreateNewOrderWithNoResolvedTimeAndNoExecutedPrice() {
        Order order = Order.place(1L, 2L, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("100.00"), "key-1234");

        assertThat(order.status(), is(equalTo(OrderStatus.NEW)));
        assertThat(order.resolvedAt(), is(nullValue()));
        assertThat(order.executedPrice(), is(nullValue()));
        assertThat(order.isTerminal(), is(false));
    }

    @Test
    @DisplayName("Should transition from NEW to FILLED and record the executed price")
    void shouldTransitionFromNewToFilledAndRecordExecutedPrice() {
        Order order = Order.place(1L, 2L, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("100.00"), "key-1234");

        order.fill(new BigDecimal("99.50"), Instant.now());

        assertThat(order.status(), is(equalTo(OrderStatus.FILLED)));
        assertThat(order.executedPrice(), is(equalTo(new BigDecimal("99.50"))));
        assertThat(order.resolvedAt(), is(notNullValue()));
        assertThat(order.isTerminal(), is(true));
    }

    @Test
    @DisplayName("Should transition from NEW to REJECTED without an executed price")
    void shouldTransitionFromNewToRejectedWithoutExecutedPrice() {
        Order order = Order.place(1L, 2L, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("100.00"), "key-1234");

        order.reject(Instant.now());

        assertThat(order.status(), is(equalTo(OrderStatus.REJECTED)));
        assertThat(order.executedPrice(), is(nullValue()));
        assertThat(order.isTerminal(), is(true));
    }

    @Test
    @DisplayName("Should transition from NEW to CANCELLED")
    void shouldTransitionFromNewToCancelled() {
        Order order = Order.place(1L, 2L, OrderSide.SELL, new BigDecimal("5"),
                new BigDecimal("50.00"), "key-5678");

        order.cancel(Instant.now());

        assertThat(order.status(), is(equalTo(OrderStatus.CANCELLED)));
        assertThat(order.isTerminal(), is(true));
    }

    @Test
    @DisplayName("Should refuse a transition out of a terminal state")
    void shouldRefuseTransitionOutOfTerminalState() {
        Order order = Order.place(1L, 2L, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("100.00"), "key-1234");
        order.fill(new BigDecimal("100.00"), Instant.now());

        assertThrows(IllegalStateException.class, () -> order.cancel(Instant.now()));
    }

    @Test
    @DisplayName("Should identify BUY and SELL sides correctly")
    void shouldIdentifyBuyAndSellSidesCorrectly() {
        Order buyOrder = Order.place(1L, 2L, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("100.00"), "key-1234");
        Order sellOrder = Order.place(1L, 2L, OrderSide.SELL, new BigDecimal("10"),
                new BigDecimal("100.00"), "key-5678");

        assertThat(buyOrder.isBuy(), is(true));
        assertThat(sellOrder.isBuy(), is(false));
    }

    @Test
    @DisplayName("Should compute notional value as quantity multiplied by limit price")
    void shouldComputeNotionalValueAsQuantityMultipliedByLimitPrice() {
        Order order = Order.place(1L, 2L, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("99.99"), "key-1234");

        assertThat(order.notionalValue(), is(equalTo(new BigDecimal("999.90"))));
    }
}