package com.yellow.trade.watchlists;

import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.notifications.api.AlertDelivery;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-10-06T09:59:58Z");
    private static final UUID EVENT = UUID.fromString("3a5c7e91-2b4d-4f60-8c1e-9d0f2a4b6c8e");

    @Mock private InstrumentMapper instruments;
    @Mock private LatestQuoteMapper latest;
    @Mock private AlertMapper alerts;
    @Mock private AlertDelivery delivery;

    private QuoteEvaluator evaluator() {
        return new QuoteEvaluator(instruments, latest, alerts, delivery, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MarketQuote quote(String price) {
        return new MarketQuote(EVENT, "ITC.NS", new BigDecimal(price), new BigDecimal("0.5"), false, AS_OF);
    }

    private void knownInstrument() {
        InstrumentRow row = new InstrumentRow();
        row.setInstrumentId(42L);
        row.setSymbol("ITC.NS");
        when(instruments.findBySymbol("ITC.NS")).thenReturn(row);
    }

    private static AlertRow active(long id, long account, String direction, String threshold) {
        AlertRow row = new AlertRow();
        row.setAlertId(id);
        row.setClientId(account);
        row.setInstrumentId(42L);
        row.setDirection(direction);
        row.setThreshold(new BigDecimal(threshold));
        row.setStatus("ACTIVE");
        return row;
    }

    @Test
    @DisplayName("every quote is kept as the instrument's latest price, for the watchlists beside it")
    void keepsTheLatestPrice() {
        knownInstrument();
        when(latest.hold(42L, new BigDecimal("266.70"), new BigDecimal("0.5"), false, AS_OF, EVENT, NOW)).thenReturn(1);
        when(alerts.lockCrossed(42L, new BigDecimal("266.70"))).thenReturn(List.of());

        evaluator().evaluate(quote("266.70"));

        verify(latest).hold(42L, new BigDecimal("266.70"), new BigDecimal("0.5"), false, AS_OF, EVENT, NOW);
        verifyNoInteractions(delivery);
    }

    @Test
    @DisplayName("a crossed alert is handed to notifications, then marked TRIGGERED with what it queued")
    void crossedAlertIsDelivered() {
        knownInstrument();
        UUID queued = UUID.randomUUID();
        when(latest.hold(anyLong(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(1);
        when(alerts.lockCrossed(42L, new BigDecimal("271.00"))).thenReturn(List.of(active(5L, 3L, "ABOVE", "270")));
        when(delivery.deliver(any())).thenReturn(new DeliveryReceipt(queued, false));

        evaluator().evaluate(quote("271.00"));

        ArgumentCaptor<AlertNotice> notice = ArgumentCaptor.forClass(AlertNotice.class);
        verify(delivery).deliver(notice.capture());
        assertThat(notice.getValue().eventId(), is(EVENT));
        assertThat(notice.getValue().accountId(), is(3L));
        assertThat(notice.getValue().alertId(), is(5L));
        assertThat(notice.getValue().symbol(), is("ITC.NS"));
        assertThat(notice.getValue().direction(), is(AlertNotice.Direction.ABOVE));
        assertThat(notice.getValue().threshold(), is(new BigDecimal("270")));
        assertThat(notice.getValue().price(), is(new BigDecimal("271.00")));
        assertThat(notice.getValue().quoteAsOf(), is(AS_OF));
        verify(alerts).markTriggered(5L, new BigDecimal("271.00"), NOW, queued);
    }

    @Test
    @DisplayName("delivery failing fails the whole quote: nothing is marked, so the alert stays ACTIVE for the next one")
    void deliveryFailureMarksNothing() {
        knownInstrument();
        when(latest.hold(anyLong(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(1);
        when(alerts.lockCrossed(eq(42L), any())).thenReturn(List.of(active(5L, 3L, "BELOW", "270")));
        when(delivery.deliver(any())).thenThrow(new IllegalStateException("ledger down"));

        assertThrows(IllegalStateException.class, () -> evaluator().evaluate(quote("269.00")));

        verify(alerts, never()).markTriggered(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("a quote observed before the one already held changes nothing and fires nothing")
    void outOfOrder() {
        knownInstrument();
        when(latest.hold(anyLong(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(0);

        evaluator().evaluate(quote("271.00"));

        verify(alerts, never()).lockCrossed(anyLong(), any());
        verifyNoInteractions(delivery);
    }

    @Test
    @DisplayName("a symbol the platform does not list is passed by: nobody can watch it")
    void unknownSymbol() {
        when(instruments.findBySymbol("ITC.NS")).thenReturn(null);

        evaluator().evaluate(quote("271.00"));

        verifyNoInteractions(latest, alerts, delivery);
    }
}
