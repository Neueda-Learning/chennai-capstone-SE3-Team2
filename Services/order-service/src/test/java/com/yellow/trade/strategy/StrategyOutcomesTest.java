package com.yellow.trade.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyOutcomesTest {

    private static final UUID EVENT = UUID.fromString("d47f9a10-3e2b-4c88-b0a1-7e6d5c4b3a29");
    private static final UUID ORDER = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");

    @Mock private StrategyMapper strategies;

    private StrategyOutcomes outcomes() {
        return new StrategyOutcomes(strategies, new ObjectMapper(), Clock.fixed(Instant.parse("2026-10-07T05:00:00Z"), ZoneOffset.UTC));
    }

    private static String event(String type, String executedPrice, String reason) {
        return """
                {"eventId":"%s","eventType":"%s","eventTime":"2026-10-07T05:00:00Z","source":"trade-executor",
                 "schemaVersion":1,"payload":{"orderId":"%s","accountId":3,"symbol":"ITC.NS","side":"BUY","quantity":2,
                 "price":260.85,"executedPrice":%s,"status":"X","reason":%s,"cashDelta":0,
                 "positionQuantityAfter":null,"averageCostAfter":null,"executedOn":"2026-10-07T05:00:00Z"}}"""
                .formatted(EVENT, type, ORDER, executedPrice, reason == null ? "null" : "\"" + reason + "\"");
    }

    private RunRow run() {
        ArgumentCaptor<RunRow> run = ArgumentCaptor.forClass(RunRow.class);
        verify(strategies, org.mockito.Mockito.atLeastOnce()).insertRun(run.capture());
        return run.getAllValues().get(0);
    }

    @Test
    @DisplayName("a strategy's order filled: a FILLED run at the executed price")
    void filled() {
        when(strategies.findByPlacedOrder(ORDER)).thenReturn(5L);
        when(strategies.insertRun(any())).thenReturn(1);

        outcomes().apply(event("ORDER_FILLED", "259.60", null));

        RunRow run = run();
        assertThat(run.getStrategyId(), is(5L));
        assertThat(run.getOutcome(), is("FILLED"));
        assertThat(run.getQuotePrice(), comparesEqualTo(new BigDecimal("259.60")));
        assertThat(run.getSourceEventId(), is(EVENT));
        verify(strategies, never()).recordFailure(anyLong());
    }

    @Test
    @DisplayName("rejected: a REJECTED run saying why, and a failure counted (armed again to try at the next quote)")
    void rejected() {
        when(strategies.findByPlacedOrder(ORDER)).thenReturn(5L);
        when(strategies.insertRun(any())).thenReturn(1);
        when(strategies.recordFailure(5L)).thenReturn(1);

        outcomes().apply(event("ORDER_REJECTED", "null", "PRICE_NOT_MET"));

        assertThat(run().getOutcome(), is("REJECTED"));
        assertThat(run().getReason(), is("PRICE_NOT_MET"));
        verify(strategies).recordFailure(5L);
    }

    @Test
    @DisplayName("the third rejection stops it, and a STOPPED run says so")
    void thirdRejection() {
        when(strategies.findByPlacedOrder(ORDER)).thenReturn(5L);
        when(strategies.insertRun(any())).thenReturn(1);
        when(strategies.recordFailure(5L)).thenReturn(3);

        outcomes().apply(event("ORDER_REJECTED", "null", "INSUFFICIENT_FUNDS"));

        ArgumentCaptor<RunRow> runs = ArgumentCaptor.forClass(RunRow.class);
        verify(strategies, times(2)).insertRun(runs.capture());
        assertThat(runs.getAllValues().get(1).getOutcome(), is("STOPPED"));
    }

    @Test
    @DisplayName("an order no strategy placed is none of this module's business")
    void notAStrategysOrder() {
        when(strategies.findByPlacedOrder(ORDER)).thenReturn(null);

        outcomes().apply(event("ORDER_FILLED", "259.60", null));

        verify(strategies, never()).insertRun(any());
    }

    @Test
    @DisplayName("a replayed outcome records nothing twice and counts no failure twice")
    void replay() {
        when(strategies.findByPlacedOrder(ORDER)).thenReturn(5L);
        when(strategies.insertRun(any())).thenReturn(0);

        outcomes().apply(event("ORDER_REJECTED", "null", "PRICE_NOT_MET"));

        verify(strategies, never()).recordFailure(anyLong());
    }
}
