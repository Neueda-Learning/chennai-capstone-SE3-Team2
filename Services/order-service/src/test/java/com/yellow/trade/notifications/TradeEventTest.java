package com.yellow.trade.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yellow.trade.notifications.NotificationExceptions.UnreadableEventException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeEventTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    /** The ORDER_FILLED example in Contracts/API Schemas/kafka-topics.md, with a field nobody knows yet. */
    private static final String FILLED = """
            {
              "eventId": "d47f9a10-3e2b-4c88-b0a1-7e6d5c4b3a29",
              "eventType": "ORDER_FILLED",
              "eventTime": "2026-09-28T09:14:24Z",
              "source": "trade-executor",
              "schemaVersion": 1,
              "payload": {
                "orderId": "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
                "accountId": 1,
                "symbol": "AAPL",
                "side": "BUY",
                "quantity": 100,
                "price": 233.00,
                "executedPrice": 232.71,
                "status": "FILLED",
                "reason": null,
                "cashDelta": -23271.00,
                "positionQuantityAfter": 100,
                "averageCostAfter": 232.71,
                "executedOn": "2026-09-28T09:14:24Z",
                "aFieldFromTheFuture": true
              }
            }""";

    private static String with(String from, String to) {
        return FILLED.replace(from, to);
    }

    @Test
    @DisplayName("reads the contract's example, ignoring a field it does not know")
    void readsTheContract() {
        TradeEvent event = TradeEvent.parse(FILLED, json);

        assertThat(event.eventId(), is(UUID.fromString("d47f9a10-3e2b-4c88-b0a1-7e6d5c4b3a29")));
        assertThat(event.kind(), is(NotificationKind.ORDER_FILLED));
        assertThat(event.eventTime(), is(Instant.parse("2026-09-28T09:14:24Z")));
        assertThat(event.accountId(), is(1L));
        assertThat(event.symbol(), is("AAPL"));
        assertThat(event.side(), is("BUY"));
        assertThat(event.quantity(), is(new BigDecimal("100")));
        assertThat(event.executedPrice(), is(new BigDecimal("232.71")));
        assertThat(event.reason(), is(nullValue()));
    }

    @Test
    @DisplayName("a rejection and a cancellation carry no executed price, and a reason")
    void rejectionAndCancellation() {
        TradeEvent rejected = TradeEvent.parse(with("\"ORDER_FILLED\"", "\"ORDER_REJECTED\"")
                .replace("\"executedPrice\": 232.71", "\"executedPrice\": null")
                .replace("\"reason\": null", "\"reason\": \"PRICE_NOT_MET\""), json);
        TradeEvent cancelled = TradeEvent.parse(with("\"ORDER_FILLED\"", "\"ORDER_CANCELLED\"")
                .replace("\"executedPrice\": 232.71", "\"executedPrice\": null")
                .replace("\"reason\": null", "\"reason\": \"CANCELLED_BY_CUSTOMER\""), json);

        assertThat(rejected.kind(), is(NotificationKind.ORDER_REJECTED));
        assertThat(rejected.reason(), is("PRICE_NOT_MET"));
        assertThat(cancelled.kind(), is(NotificationKind.ORDER_CANCELLED));
        assertThat(cancelled.executedPrice(), is(nullValue()));
    }

    @Test
    @DisplayName("an event type trade-events does not carry can never be processed: refused, to dead-letter")
    void unknownType() {
        assertThrows(UnreadableEventException.class,
                () -> TradeEvent.parse(with("\"ORDER_FILLED\"", "\"ORDER_PLACED\""), json));
        assertThrows(UnreadableEventException.class,
                () -> TradeEvent.parse(with("\"ORDER_FILLED\"", "\"PRICE_ALERT\""), json));
    }

    @Test
    @DisplayName("no event id, no account, no symbol, or a fill with no price: refused, never guessed")
    void missingFields() {
        for (String broken : new String[] {
                with("\"eventId\": \"d47f9a10-3e2b-4c88-b0a1-7e6d5c4b3a29\",", ""),
                with("\"eventId\": \"d47f9a10-3e2b-4c88-b0a1-7e6d5c4b3a29\"", "\"eventId\": \"not-a-uuid\""),
                with("\"accountId\": 1,", ""),
                with("\"symbol\": \"AAPL\",", ""),
                with("\"executedPrice\": 232.71,", "\"executedPrice\": null,"),
                with("\"side\": \"BUY\"", "\"side\": \"HOLD\""),
                "not json at all",
                "{}"}) {
            assertThrows(UnreadableEventException.class, () -> TradeEvent.parse(broken, json), broken);
        }
    }
}
