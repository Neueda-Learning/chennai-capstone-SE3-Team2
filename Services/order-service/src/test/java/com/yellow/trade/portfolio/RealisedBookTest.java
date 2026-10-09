package com.yellow.trade.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.OrderRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealisedBookTest {

    private static final UUID EVENT = UUID.fromString("d47f9a10-3e2b-4c88-b0a1-7e6d5c4b3a29");
    private static final UUID ORDER = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");

    @Mock private RealisedMapper realised;
    @Mock private OrderMapper orders;

    private final ObjectMapper json = new ObjectMapper();

    private RealisedBook book() {
        return new RealisedBook(realised, orders, json);
    }

    private static String event(String type, String side, String executedPrice, String averageCostAfter) {
        return """
                {"eventId":"%s","eventType":"%s","eventTime":"2026-10-07T04:00:00Z","source":"trade-executor",
                 "schemaVersion":1,"payload":{"orderId":"%s","accountId":3,"symbol":"ITC.NS","side":"%s","quantity":10,
                 "price":260.00,"executedPrice":%s,"status":"X","reason":null,"cashDelta":2667.0,
                 "positionQuantityAfter":0,"averageCostAfter":%s,"executedOn":"2026-10-07T04:00:01Z"}}"""
                .formatted(EVENT, type, ORDER, side, executedPrice, averageCostAfter);
    }

    private static OrderRow order(long account, OrderSide side, OrderStatus status) {
        OrderRow row = new OrderRow();
        row.setOrderId(ORDER);
        row.setClientId(account);
        row.setInstrumentId(42L);
        row.setSide(side);
        row.setStatus(status);
        return row;
    }

    @Test
    @DisplayName("a sale books (sale price - average cost at the sale) * quantity, keyed on its event")
    void bookedAtTheSale() {
        when(orders.findById(ORDER)).thenReturn(order(3L, OrderSide.SELL, OrderStatus.FILLED));
        when(realised.insert(any())).thenReturn(1);

        assertThat(book().book(event("ORDER_FILLED", "SELL", "266.70", "240.50")), is(true));

        ArgumentCaptor<RealisedRow> row = ArgumentCaptor.forClass(RealisedRow.class);
        verify(realised).insert(row.capture());
        assertThat(row.getValue().getEventId(), is(EVENT));
        assertThat(row.getValue().getOrderId(), is(ORDER));
        assertThat(row.getValue().getClientId(), is(3L));
        assertThat(row.getValue().getInstrumentId(), is(42L));
        assertThat(row.getValue().getRealised(), comparesEqualTo(new BigDecimal("262.0000")));
        assertThat(row.getValue().getAverageCost(), comparesEqualTo(new BigDecimal("240.50")));
        assertThat(row.getValue().getBookedAt(), is(Instant.parse("2026-10-07T04:00:01Z")));
    }

    @Test
    @DisplayName("a sale below cost books a loss")
    void loss() {
        when(orders.findById(ORDER)).thenReturn(order(3L, OrderSide.SELL, OrderStatus.FILLED));
        when(realised.insert(any())).thenReturn(1);

        book().book(event("ORDER_FILLED", "SELL", "230", "240.50"));

        ArgumentCaptor<RealisedRow> row = ArgumentCaptor.forClass(RealisedRow.class);
        verify(realised).insert(row.capture());
        assertThat(row.getValue().getRealised(), comparesEqualTo(new BigDecimal("-105.0000")));
    }

    @Test
    @DisplayName("a buy, a rejection or a cancellation books nothing: only a sale realises anything")
    void onlySales() {
        assertThat(book().book(event("ORDER_FILLED", "BUY", "266.70", "240.50")), is(false));
        assertThat(book().book(event("ORDER_REJECTED", "SELL", "null", "null")), is(false));
        assertThat(book().book(event("ORDER_CANCELLED", "SELL", "null", "null")), is(false));
        verifyNoInteractions(realised, orders);
    }

    @Test
    @DisplayName("a sale the orders table never recorded, or one on another account, is not booked")
    void checkedAgainstOrders() {
        when(orders.findById(ORDER)).thenReturn(null, order(4L, OrderSide.SELL, OrderStatus.FILLED),
                order(3L, OrderSide.BUY, OrderStatus.FILLED), order(3L, OrderSide.SELL, OrderStatus.REJECTED));

        for (int i = 0; i < 4; i++) {
            assertThat(book().book(event("ORDER_FILLED", "SELL", "266.70", "240.50")), is(false));
        }
        verify(realised, never()).insert(any());
    }

    @Test
    @DisplayName("a sale with no average cost is not booked: a realised figure is never a guess")
    void noAverageCost() {
        when(orders.findById(ORDER)).thenReturn(order(3L, OrderSide.SELL, OrderStatus.FILLED));

        assertThat(book().book(event("ORDER_FILLED", "SELL", "266.70", "null")), is(false));
        verify(realised, never()).insert(any());
    }

    @Test
    @DisplayName("a replayed sale is a no-op: the key already holds it")
    void replay() {
        when(orders.findById(ORDER)).thenReturn(order(3L, OrderSide.SELL, OrderStatus.FILLED));
        when(realised.insert(any())).thenReturn(0);

        assertThat(book().book(event("ORDER_FILLED", "SELL", "266.70", "240.50")), is(false));
    }

    @Test
    @DisplayName("what can never be read is refused, to dead-letter")
    void unreadable() {
        assertThrows(RealisedBook.UnreadableEventException.class, () -> book().book("not json"));
        assertThrows(RealisedBook.UnreadableEventException.class,
                () -> book().book(event("ORDER_FILLED", "SELL", "266.70", "240.50").replace("\"eventId\":\"" + EVENT + "\",", "")));
    }
}
