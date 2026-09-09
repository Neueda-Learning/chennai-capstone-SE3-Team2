package com.yellow.entities;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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


    @ParameterizedTest(name = "isBuy() should be {1} for side {0}")
    @DisplayName("Should identify BUY and SELL sides correctly")
    @CsvSource({
            "BUY,  true",
            "SELL, false"
    })
    void shouldIdentifyBuyAndSellSidesCorrectly(OrderSide side, boolean expectedIsBuy) {
        Order order = Order.place(1L, 2L, side, new BigDecimal("10"), new BigDecimal("100.00"), "key-1234");
        assertThat(order.isBuy(), is(expectedIsBuy));
    }

    @Test
    @DisplayName("Should compute notional value as quantity multiplied by limit price")
    void shouldComputeNotionalValueAsQuantityMultipliedByLimitPrice() {
        Order order = Order.place(1L, 2L, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("99.99"), "key-1234");

        assertThat(order.notionalValue(), is(equalTo(new BigDecimal("999.90"))));
    }

    @Test
    @DisplayName("Should refuse a NEW order that already carries a resolved time")
    void shouldRefuseNewOrderThatAlreadyCarriesResolvedTime() {
        assertThrows(IllegalArgumentException.class, () -> new Order(
                UUID.randomUUID(), 1L, 2L, OrderSide.BUY, new BigDecimal("10"), new BigDecimal("100.00"),
                null, OrderStatus.NEW, "key-1234", Instant.now(), Instant.now()));
    }

    @Test
    @DisplayName("Should refuse a terminal-status order with no resolved time")
    void shouldRefuseTerminalStatusOrderWithNoResolvedTime() {
        assertThrows(IllegalArgumentException.class, () -> new Order(
                UUID.randomUUID(), 1L, 2L, OrderSide.BUY, new BigDecimal("10"), new BigDecimal("100.00"),
                new BigDecimal("100.00"), OrderStatus.FILLED, "key-1234", Instant.now(), null));
    }

    @Test
    @DisplayName("Should refuse a FILLED order with no executed price")
    void shouldRefuseFilledOrderWithNoExecutedPrice() {
        assertThrows(IllegalArgumentException.class, () -> new Order(
                UUID.randomUUID(), 1L, 2L, OrderSide.BUY, new BigDecimal("10"), new BigDecimal("100.00"),
                null, OrderStatus.FILLED, "key-1234", Instant.now(), Instant.now()));
    }

    @Test
    @DisplayName("Should expose account id, instrument id, idempotency key and placed time")
    void shouldExposeAccountInstrumentIdempotencyKeyAndPlacedTime() {
        Order order = Order.place(1L, 2L, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("100.00"), "key-1234");

        assertThat(order.accountId(), is(equalTo(1L)));
        assertThat(order.instrumentId(), is(equalTo(2L)));
        assertThat(order.idempotencyKey(), is(equalTo("key-1234")));
        assertThat(order.placedAt(), is(notNullValue()));
        assertThat(order.toString(), containsString("BUY"));
    }
}