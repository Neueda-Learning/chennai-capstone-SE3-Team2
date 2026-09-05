package com.yellow.entities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PositionTest {

    @Test
    @DisplayName("Should open a position at the given quantity and price")
    void shouldOpenPositionAtGivenQuantityAndPrice() {
        Position position = Position.opening(1L, 2L, new BigDecimal("10"), new BigDecimal("100.00"));

        assertThat(position.quantity(), is(equalTo(new BigDecimal("10.000000"))));
        assertThat(position.averagePrice(), is(equalTo(new BigDecimal("100.0000"))));
    }

    @Test
    @DisplayName("Should recalculate the average price across old and new units on a buy")
    void shouldRecalculateAveragePriceAcrossOldAndNewUnitsOnBuy() {
        Position position = Position.opening(1L, 2L, new BigDecimal("10"), new BigDecimal("100.00"));

        position.applyBuy(new BigDecimal("10"), new BigDecimal("200.00"));

        // (10 units @ 100) + (10 units @ 200), over 20 units -> average of 150
        assertThat(position.quantity(), is(equalTo(new BigDecimal("20.000000"))));
        assertThat(position.averagePrice(), is(equalTo(new BigDecimal("150.0000"))));
    }

    @Test
    @DisplayName("Should reduce quantity on a sell while leaving the average price unchanged")
    void shouldReduceQuantityOnSellWhileLeavingAveragePriceUnchanged() {
        Position position = Position.opening(1L, 2L, new BigDecimal("10"), new BigDecimal("100.00"));

        position.applySell(new BigDecimal("4"));

        assertThat(position.quantity(), is(equalTo(new BigDecimal("6.000000"))));
        assertThat("a sell must not touch the average cost",
                position.averagePrice(), is(equalTo(new BigDecimal("100.0000"))));
    }

    @Test
    @DisplayName("Should refuse a sell larger than the held quantity")
    void shouldRefuseSellLargerThanHeldQuantity() {
        Position position = Position.opening(1L, 2L, new BigDecimal("10"), new BigDecimal("100.00"));

        assertThrows(IllegalStateException.class, () -> position.applySell(new BigDecimal("11")));
        assertThat("holding must be unchanged after a refused sell",
                position.quantity(), is(equalTo(new BigDecimal("10.000000"))));
    }

    @Test
    @DisplayName("Should report canSell as true only up to the held quantity")
    void shouldReportCanSellAsTrueOnlyUpToHeldQuantity() {
        Position position = Position.opening(1L, 2L, new BigDecimal("10"), new BigDecimal("100.00"));

        assertThat(position.canSell(new BigDecimal("10")), is(true));
        assertThat(position.canSell(new BigDecimal("10.000001")), is(false));
    }

    @Test
    @DisplayName("Should be closed once the full held quantity has been sold")
    void shouldBeClosedOnceFullHeldQuantityHasBeenSold() {
        Position position = Position.opening(1L, 2L, new BigDecimal("10"), new BigDecimal("100.00"));

        position.applySell(new BigDecimal("10"));

        assertThat(position.isClosed(), is(true));
    }

    @Test
    @DisplayName("Should compute invested value as quantity multiplied by average price")
    void shouldComputeInvestedValueAsQuantityMultipliedByAveragePrice() {
        Position position = Position.opening(1L, 2L, new BigDecimal("10"), new BigDecimal("100.00"));

        // compareTo rather than equals() -- multiply() does not force a scale,
        // so the raw result carries more decimal places than either operand.
        assertThat(position.investedValue(), comparesEqualTo(new BigDecimal("1000")));
    }
}