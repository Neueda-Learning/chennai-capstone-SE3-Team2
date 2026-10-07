package com.yellow.trade.strategy;

import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.mappers.PositionRow;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyFirerTest {

    private static final Instant NOW = Instant.parse("2026-10-07T05:00:00Z");
    private static final UUID QUOTE = UUID.fromString("3a5c7e91-2b4d-4f60-8c1e-9d0f2a4b6c8e");
    private static final UUID ORDER = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");

    @Mock private StrategyMapper strategies;
    @Mock private PositionMapper positions;
    @Mock private OrderPlacer placer;

    private StrategyFirer firer() {
        return new StrategyFirer(strategies, positions, placer, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Buy 2 ITC.NS when it falls to 260; at most 1,000 a firing, at most 10 held. */
    private static StrategyRow buy() {
        StrategyRow row = new StrategyRow();
        row.setStrategyId(5L);
        row.setClientId(3L);
        row.setInstrumentId(42L);
        row.setSymbol("ITC.NS");
        row.setSide("BUY");
        row.setQuantity(2);
        row.setTriggerKind("FALLS_THROUGH");
        row.setTriggerPrice(new BigDecimal("260"));
        row.setMaxSpend(new BigDecimal("1000"));
        row.setMaxPosition(10);
        row.setEnabled(true);
        row.setStatus("ARMED");
        return row;
    }

    private static StrategyQuote quote(String price, String bid, String ask) {
        return new StrategyQuote(QUOTE, "ITC.NS", new BigDecimal(price), new BigDecimal(bid), new BigDecimal(ask), NOW);
    }

    private RunRow recordedRun() {
        ArgumentCaptor<RunRow> run = ArgumentCaptor.forClass(RunRow.class);
        verify(strategies).insertRun(run.capture());
        return run.getValue();
    }

    @Test
    @DisplayName("fires on the crossing quote: a limit buy just above the ask, through the order route, and FIRED")
    void fires() {
        when(strategies.lockFireable(5L)).thenReturn(buy());
        when(strategies.insertRun(any())).thenReturn(1);
        when(placer.place(eq(3L), eq("ITC.NS"), eq("BUY"), eq(2), any(), anyString()))
                .thenReturn(OrderPlacer.Result.placed(ORDER));

        firer().fire(5L, quote("259.50", "259.45", "259.55"));

        ArgumentCaptor<BigDecimal> limit = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(placer).place(eq(3L), eq("ITC.NS"), eq("BUY"), eq(2), limit.capture(), key.capture());
        // 259.55 + half a per cent, rounded up to the paisa: room for the price to move before the fill.
        assertThat(limit.getValue(), comparesEqualTo(new BigDecimal("260.85")));
        assertThat(key.getValue(), is("strategy-5-" + QUOTE));
        RunRow run = recordedRun();
        assertThat(run.getOutcome(), is("PLACED"));
        assertThat(run.getOrderId(), is(ORDER));
        assertThat(run.getSourceEventId(), is(QUOTE));
        verify(strategies).markFired(5L, NOW);
    }

    @Test
    @DisplayName("a disabled strategy does not fire: the lock finds nothing to fire, and nothing is asked of the route")
    void disabled() {
        when(strategies.lockFireable(5L)).thenReturn(null);

        firer().fire(5L, quote("259.50", "259.45", "259.55"));

        verifyNoInteractions(placer);
        verify(strategies, never()).insertRun(any());
    }

    @Test
    @DisplayName("a quote short of the level does not fire, even if it reached the firer")
    void shortOfIt() {
        when(strategies.lockFireable(5L)).thenReturn(buy());

        firer().fire(5L, quote("260.01", "260.00", "260.02"));

        verifyNoInteractions(placer);
    }

    @Test
    @DisplayName("past the spend: refused, recorded, and STOPPED until the customer looks; nothing is placed")
    void pastTheSpend() {
        StrategyRow row = buy();
        row.setQuantity(5);
        when(strategies.lockFireable(5L)).thenReturn(row);
        when(strategies.insertRun(any())).thenReturn(1);

        firer().fire(5L, quote("259.50", "259.45", "259.55"));

        verifyNoInteractions(placer);
        RunRow run = recordedRun();
        assertThat(run.getOutcome(), is("REFUSED_LIMIT"));
        assertThat(run.getReason(), containsString("more than the ₹1,000.00"));
        verify(strategies).stop(5L);
    }

    @Test
    @DisplayName("past the position: refused, recorded, STOPPED")
    void pastThePosition() {
        when(strategies.lockFireable(5L)).thenReturn(buy());
        PositionRow held = new PositionRow();
        held.setQuantity(new BigDecimal("9"));
        when(positions.findOne(3L, 42L, "DELIVERY")).thenReturn(held);
        when(strategies.insertRun(any())).thenReturn(1);

        firer().fire(5L, quote("259.50", "259.45", "259.55"));

        verifyNoInteractions(placer);
        RunRow run = recordedRun();
        assertThat(run.getOutcome(), is("REFUSED_LIMIT"));
        assertThat(run.getReason(), containsString("11"));
        verify(strategies).stop(5L);
    }

    @Test
    @DisplayName("the route refusing is a failure: recorded, and counted; armed again to try at the next quote")
    void failure() {
        when(strategies.lockFireable(5L)).thenReturn(buy());
        when(strategies.insertRun(any())).thenReturn(1);
        when(placer.place(anyLong(), anyString(), anyString(), any(Integer.class), any(), anyString()))
                .thenReturn(OrderPlacer.Result.failed("ORD-400: insufficient funds"));
        when(strategies.recordFailure(5L)).thenReturn(1);

        firer().fire(5L, quote("259.50", "259.45", "259.55"));

        RunRow run = recordedRun();
        assertThat(run.getOutcome(), is("FAILED"));
        assertThat(run.getReason(), is("ORD-400: insufficient funds"));
        verify(strategies, never()).markFired(anyLong(), any());
    }

    @Test
    @DisplayName("stops after three failures, and says so in a run")
    void stopsAfterThree() {
        when(strategies.lockFireable(5L)).thenReturn(buy());
        when(strategies.insertRun(any())).thenReturn(1);
        when(placer.place(anyLong(), anyString(), anyString(), any(Integer.class), any(), anyString()))
                .thenReturn(OrderPlacer.Result.failed("the order route could not be reached"));
        when(strategies.recordFailure(5L)).thenReturn(3);

        firer().fire(5L, quote("259.50", "259.45", "259.55"));

        ArgumentCaptor<RunRow> runs = ArgumentCaptor.forClass(RunRow.class);
        verify(strategies, org.mockito.Mockito.times(2)).insertRun(runs.capture());
        assertThat(runs.getAllValues().get(1).getOutcome(), is("STOPPED"));
        assertThat(runs.getAllValues().get(1).getReason(), containsString("three"));
    }

    @Test
    @DisplayName("a replayed quote fires once: its run is already there, so nothing is placed again")
    void replay() {
        when(strategies.lockFireable(5L)).thenReturn(buy());
        when(strategies.insertRun(any())).thenReturn(0);

        firer().fire(5L, quote("259.50", "259.45", "259.55"));

        verifyNoInteractions(placer);
        verify(strategies, never()).markFired(anyLong(), any());
    }

    @Test
    @DisplayName("a sale sells just below the bid, and is held to no spend or position")
    void sell() {
        StrategyRow row = buy();
        row.setSide("SELL");
        row.setTriggerKind("RISES_THROUGH");
        row.setTriggerPrice(new BigDecimal("270"));
        row.setMaxSpend(new BigDecimal("1"));
        when(strategies.lockFireable(5L)).thenReturn(row);
        when(strategies.insertRun(any())).thenReturn(1);
        when(placer.place(eq(3L), eq("ITC.NS"), eq("SELL"), eq(2), any(), anyString()))
                .thenReturn(OrderPlacer.Result.placed(ORDER));

        firer().fire(5L, quote("270.50", "270.40", "270.60"));

        ArgumentCaptor<BigDecimal> limit = ArgumentCaptor.forClass(BigDecimal.class);
        verify(placer).place(eq(3L), eq("ITC.NS"), eq("SELL"), eq(2), limit.capture(), anyString());
        assertThat(limit.getValue(), comparesEqualTo(new BigDecimal("269.04")));
    }
}
