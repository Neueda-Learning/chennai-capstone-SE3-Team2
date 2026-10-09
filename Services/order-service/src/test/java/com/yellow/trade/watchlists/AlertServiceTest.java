package com.yellow.trade.watchlists;

import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.watchlists.WatchExceptions.FundAlertException;
import com.yellow.trade.watchlists.WatchExceptions.LimitReachedException;
import com.yellow.trade.watchlists.WatchExceptions.NotFoundException;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    @Mock private AlertMapper alerts;
    @Mock private WatchlistMapper watchlists;
    @Mock private InstrumentMapper instruments;
    @Mock private AccountAccess access;

    private AlertService service() {
        return new AlertService(alerts, watchlists, instruments, access, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InstrumentRow instrument(String type, boolean tradable) {
        InstrumentRow row = new InstrumentRow();
        row.setInstrumentId(42L);
        row.setSymbol("ITC.NS");
        row.setInstrumentType(type);
        row.setTradable(tradable);
        return row;
    }

    private static AlertRow stored(String status) {
        AlertRow row = new AlertRow();
        row.setAlertId(5L);
        row.setClientId(3L);
        row.setInstrumentId(42L);
        row.setSymbol("ITC.NS");
        row.setDirection("ABOVE");
        row.setThreshold(new BigDecimal("270"));
        row.setStatus(status);
        row.setCreatedAt(NOW);
        return row;
    }

    private static AlertRequest above(String threshold) {
        return new AlertRequest("ITC.NS", AlertDirection.ABOVE, new BigDecimal(threshold));
    }

    @Test
    @DisplayName("an alert is set ACTIVE on the instrument the symbol names")
    void creates() {
        when(instruments.findBySymbol("ITC.NS")).thenReturn(instrument("STOCK", true));
        when(alerts.countActive(3L)).thenReturn(0);
        when(alerts.insert(any())).thenAnswer(call -> {
            AlertRow row = call.getArgument(0);
            row.setAlertId(5L);
            return 1;
        });
        when(alerts.findOwned(3L, 5L)).thenReturn(stored("ACTIVE"));

        PriceAlert alert = service().create(3L, above("270"));

        ArgumentCaptor<AlertRow> row = ArgumentCaptor.forClass(AlertRow.class);
        verify(alerts).insert(row.capture());
        assertThat(row.getValue().getInstrumentId(), is(42L));
        assertThat(row.getValue().getDirection(), is("ABOVE"));
        assertThat(row.getValue().getCreatedAt(), is(NOW));
        assertThat(alert.status(), is(AlertStatus.ACTIVE));
        verify(watchlists).lockAccount(3L);
    }

    @Test
    @DisplayName("a twenty-first active alert is LIM-409")
    void activeCap() {
        when(instruments.findBySymbol("ITC.NS")).thenReturn(instrument("STOCK", true));
        when(alerts.countActive(3L)).thenReturn(WatchLimits.ACTIVE_ALERTS);

        assertThrows(LimitReachedException.class, () -> service().create(3L, above("270")));
        verify(alerts, never()).insert(any());
    }

    @Test
    @DisplayName("a fund is refused, VAL-422: it has no quote on the stream to fire on")
    void fundRefused() {
        when(instruments.findBySymbol("ITC.NS")).thenReturn(instrument("MF", true));

        assertThrows(FundAlertException.class, () -> service().create(3L, above("270")));
    }

    @Test
    @DisplayName("an instrument nobody lists, or one not trading, is INS-404: the poller never prices it")
    void notPriceable() {
        when(instruments.findBySymbol("ITC.NS")).thenReturn(null, instrument("STOCK", false));

        assertThrows(InstrumentNotFoundException.class, () -> service().create(3L, above("270")));
        assertThrows(InstrumentNotFoundException.class, () -> service().create(3L, above("270")));
    }

    @Test
    @DisplayName("re-arming makes a triggered alert ACTIVE again, inside the cap")
    void rearm() {
        when(alerts.findOwned(3L, 5L)).thenReturn(stored("TRIGGERED"), stored("ACTIVE"));
        when(alerts.countActive(3L)).thenReturn(3);

        assertThat(service().rearm(3L, 5L).status(), is(AlertStatus.ACTIVE));
        verify(alerts).rearm(3L, 5L);
    }

    @Test
    @DisplayName("re-arming past the cap is LIM-409; re-arming one already ACTIVE changes nothing")
    void rearmCap() {
        when(alerts.findOwned(3L, 5L)).thenReturn(stored("CANCELLED"));
        when(alerts.countActive(3L)).thenReturn(WatchLimits.ACTIVE_ALERTS);
        assertThrows(LimitReachedException.class, () -> service().rearm(3L, 5L));

        when(alerts.findOwned(3L, 5L)).thenReturn(stored("ACTIVE"));
        assertThat(service().rearm(3L, 5L).status(), is(AlertStatus.ACTIVE));
        verify(alerts, never()).rearm(anyLong(), anyLong());
    }

    @Test
    @DisplayName("another account's alert is WCH-404 to cancel or re-arm")
    void notOwned() {
        when(alerts.findOwned(3L, 5L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service().cancel(3L, 5L));
        assertThrows(NotFoundException.class, () -> service().rearm(3L, 5L));
    }
}
