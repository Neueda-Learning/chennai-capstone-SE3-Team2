package com.yellow.trade.notifications;

import com.yellow.trade.notifications.api.AlertNotice;
import com.yellow.trade.notifications.api.DeliveryReceipt;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The ledger's two doors: an order outcome from trade-events, and a crossed alert from watchlists. */
@ExtendWith(MockitoExtension.class)
class NotificationLedgerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final UUID EVENT = UUID.fromString("d47f9a10-3e2b-4c88-b0a1-7e6d5c4b3a29");

    @Mock private NotificationMapper mapper;

    private NotificationLedger ledger() {
        return new NotificationLedger(mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TradeEvent filled() {
        return new TradeEvent(EVENT, NotificationKind.ORDER_FILLED, NOW, UUID.randomUUID(), 3L, "ITC.NS", "BUY",
                new BigDecimal("2"), new BigDecimal("400"), new BigDecimal("399.5"), null);
    }

    private static AlertNotice crossed(long alertId) {
        return new AlertNotice(EVENT, 3L, alertId, "ITC.NS", AlertNotice.Direction.ABOVE, new BigDecimal("400"),
                new BigDecimal("401"), NOW);
    }

    @Test
    @DisplayName("an order outcome is recorded QUEUED, keyed on its eventId, with what the customer will read")
    void recordsATradeEvent() {
        when(mapper.insertQueued(any())).thenReturn(1);

        assertThat(ledger().record(filled()), is(true));

        ArgumentCaptor<NotificationRow> row = ArgumentCaptor.forClass(NotificationRow.class);
        verify(mapper).insertQueued(row.capture());
        assertThat(row.getValue().getEventId(), is(EVENT));
        assertThat(row.getValue().getAlertId(), is(nullValue()));
        assertThat(row.getValue().getClientId(), is(3L));
        assertThat(row.getValue().getKind(), is("ORDER_FILLED"));
        assertThat(row.getValue().getSubject(), is("Bought 2 ITC.NS at ₹399.50"));
        assertThat(row.getValue().getCreatedAt(), is(NOW));
    }

    @Test
    @DisplayName("a replayed event is a no-op: the key already holds it, and nothing new is queued")
    void replay() {
        when(mapper.insertQueued(any())).thenReturn(0);

        assertThat(ledger().record(filled()), is(false));
    }

    @Test
    @DisplayName("a crossed alert is queued under the quote and the alert, and the receipt names it")
    void deliversAnAlert() {
        when(mapper.insertQueued(any())).thenReturn(1);

        DeliveryReceipt receipt = ledger().deliver(crossed(7L));

        ArgumentCaptor<NotificationRow> row = ArgumentCaptor.forClass(NotificationRow.class);
        verify(mapper).insertQueued(row.capture());
        assertThat(row.getValue().getKind(), is("PRICE_ALERT"));
        assertThat(row.getValue().getEventId(), is(EVENT));
        assertThat(row.getValue().getAlertId(), is(7L));
        assertThat(receipt.notificationId(), is(row.getValue().getNotificationId()));
        assertThat(receipt.duplicate(), is(false));
    }

    @Test
    @DisplayName("the same quote for the same alert again answers the first receipt, marked duplicate")
    void alertReplay() {
        UUID first = UUID.randomUUID();
        when(mapper.insertQueued(any())).thenReturn(0);
        when(mapper.findIdBySource(EVENT, 7L)).thenReturn(first);

        DeliveryReceipt receipt = ledger().deliver(crossed(7L));

        assertThat(receipt.notificationId(), is(first));
        assertThat(receipt.duplicate(), is(true));
    }
}
