package com.yellow.trade.strategy;

import com.yellow.enums.OrderSide;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.security.AccountNotReachableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T05:00:00Z");

    @Mock private StrategyMapper strategies;
    @Mock private InstrumentMapper instruments;
    @Mock private AccountAccess access;
    @Mock private IndicatorReader indicators;

    private StrategyService service() {
        return new StrategyService(strategies, instruments, access, indicators, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static StrategyRequest request() {
        return new StrategyRequest("ITC.NS", OrderSide.BUY, 2, Trigger.FALLS_THROUGH, new BigDecimal("260.00"),
                new BigDecimal("1000"), 10);
    }

    private void listed(String type, boolean tradable) {
        InstrumentRow row = new InstrumentRow();
        row.setInstrumentId(42L);
        row.setSymbol("ITC.NS");
        row.setInstrumentType(type);
        row.setTradable(tradable);
        when(instruments.findBySymbol("ITC.NS")).thenReturn(row);
    }

    private static StrategyRow stored() {
        StrategyRow row = new StrategyRow();
        row.setStrategyId(5L);
        row.setClientId(3L);
        row.setSymbol("ITC.NS");
        row.setSide("BUY");
        row.setQuantity(2);
        row.setTriggerKind("FALLS_THROUGH");
        row.setTriggerPrice(new BigDecimal("260.00"));
        row.setMaxSpend(new BigDecimal("1000"));
        row.setMaxPosition(10);
        row.setStatus("ARMED");
        row.setCreatedAt(NOW);
        return row;
    }

    @Test
    @DisplayName("a strategy is created disabled and armed, on the instrument the symbol names")
    void creates() {
        listed("STOCK", true);
        when(strategies.countForClient(3L)).thenReturn(0);
        when(strategies.insert(any())).thenAnswer(call -> {
            StrategyRow row = call.getArgument(0);
            row.setStrategyId(5L);
            return 1;
        });
        when(strategies.findOwned(3L, 5L)).thenReturn(stored());

        Strategy created = service().create(3L, request());

        ArgumentCaptor<StrategyRow> row = ArgumentCaptor.forClass(StrategyRow.class);
        verify(strategies).insert(row.capture());
        assertThat(row.getValue().getInstrumentId(), is(42L));
        assertThat(row.getValue().getTriggerKind(), is("FALLS_THROUGH"));
        assertThat(created.enabled(), is(false));
        assertThat(created.status(), is(StrategyStatus.ARMED));
    }

    @Test
    @DisplayName("a falls-to or rises-to trigger needs a price; an indicator trigger takes none: VAL-422 either way, nothing stored")
    void triggerPrice() {
        StrategyRequest noPrice = new StrategyRequest("ITC.NS", OrderSide.BUY, 2, Trigger.FALLS_THROUGH, null, new BigDecimal("1000"), 10);
        StrategyRequest pricedCrossover = new StrategyRequest("ITC.NS", OrderSide.BUY, 2, Trigger.MA_CROSSOVER,
                new BigDecimal("260.00"), new BigDecimal("1000"), 10);

        assertThrows(StrategyExceptions.TriggerPriceException.class, () -> service().create(3L, noPrice));
        assertThrows(StrategyExceptions.TriggerPriceException.class, () -> service().create(3L, pricedCrossover));
        verify(strategies, never()).insert(any());
    }

    @Test
    @DisplayName("a crossover strategy is stored with no price; listed, it shows the figures it is waiting on")
    void crossover() {
        listed("STOCK", true);
        when(strategies.countForClient(3L)).thenReturn(0);
        when(strategies.insert(any())).thenAnswer(call -> {
            StrategyRow row = call.getArgument(0);
            row.setStrategyId(6L);
            return 1;
        });
        StrategyRow stored = stored();
        stored.setStrategyId(6L);
        stored.setTriggerKind("MA_CROSSOVER");
        stored.setTriggerPrice(null);
        when(strategies.findOwned(3L, 6L)).thenReturn(stored);
        when(indicators.latest("ITC.NS")).thenReturn(java.util.Optional.of(
                new Indicators.View(new BigDecimal("259.50"), NOW, 80, 255.0, 256.0, 255.5, 256.0, 240.0, 270.0)));

        Strategy created = service().create(3L, new StrategyRequest("ITC.NS", OrderSide.BUY, 2, Trigger.MA_CROSSOVER, null,
                new BigDecimal("1000"), 10));

        ArgumentCaptor<StrategyRow> row = ArgumentCaptor.forClass(StrategyRow.class);
        verify(strategies).insert(row.capture());
        assertThat(row.getValue().getTriggerKind(), is("MA_CROSSOVER"));
        assertThat(row.getValue().getTriggerPrice(), is(org.hamcrest.Matchers.nullValue()));
        assertThat(created.indicator().shortAverage(), comparesEqualTo(new BigDecimal("255.50")));
        assertThat(created.indicator().longAverage(), comparesEqualTo(new BigDecimal("256.00")));
        assertThat(created.indicator().price(), comparesEqualTo(new BigDecimal("259.50")));
    }

    @Test
    @DisplayName("an eleventh strategy is LIM-409, decided under the account's lock")
    void cap() {
        listed("STOCK", true);
        when(strategies.countForClient(3L)).thenReturn(StrategyService.MAX_STRATEGIES);

        assertThrows(StrategyExceptions.LimitReachedException.class, () -> service().create(3L, request()));

        InOrder order = inOrder(strategies);
        order.verify(strategies).lockAccount(3L);
        order.verify(strategies).countForClient(3L);
        verify(strategies, never()).insert(any());
    }

    @Test
    @DisplayName("a fund is refused: it has no live quote to fire on; an instrument nobody lists, or not trading, INS-404")
    void refusedInstruments() {
        listed("MF", true);
        assertThrows(StrategyExceptions.FundStrategyException.class, () -> service().create(3L, request()));

        listed("STOCK", false);
        assertThrows(InstrumentNotFoundException.class, () -> service().create(3L, request()));

        when(instruments.findBySymbol("ITC.NS")).thenReturn(null);
        assertThrows(InstrumentNotFoundException.class, () -> service().create(3L, request()));
    }

    @Test
    @DisplayName("another account is ACC-403 before anything is read")
    void anotherAccount() {
        doThrow(new AccountNotReachableException()).when(access).requireOwn(4L);

        assertThrows(AccountNotReachableException.class, () -> service().list(4L));
        assertThrows(AccountNotReachableException.class, () -> service().setEnabled(4L, 5L, true));
        verifyNoInteractions(strategies);
    }

    @Test
    @DisplayName("another account's strategy is STR-404, to switch, to delete or to read the runs of")
    void notOwned() {
        when(strategies.findOwned(3L, 77L)).thenReturn(null);
        when(strategies.delete(3L, 77L)).thenReturn(0);

        assertThrows(StrategyExceptions.NotFoundException.class, () -> service().setEnabled(3L, 77L, true));
        assertThrows(StrategyExceptions.NotFoundException.class, () -> service().runs(3L, 77L));
        assertThrows(StrategyExceptions.NotFoundException.class, () -> service().delete(3L, 77L));
        verify(strategies, never()).enable(3L, 77L);
    }

    @Test
    @DisplayName("switching off is one update; switching on re-arms one that fired or stopped")
    void switching() {
        when(strategies.findOwned(3L, 5L)).thenReturn(stored());

        service().setEnabled(3L, 5L, false);
        service().setEnabled(3L, 5L, true);

        verify(strategies).disable(3L, 5L);
        verify(strategies).enable(3L, 5L);
    }
}
