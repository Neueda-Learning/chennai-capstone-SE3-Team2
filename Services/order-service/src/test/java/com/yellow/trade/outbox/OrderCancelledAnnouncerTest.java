package com.yellow.trade.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yellow.enums.OrderSide;
import com.yellow.trade.events.OrderCancelledDomainEvent;
import com.yellow.trade.mappers.OrderRow;
import com.yellow.trade.mappers.OutboxMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderCancelledAnnouncerTest {

    private static final Instant AT = Instant.parse("2026-10-06T10:00:00Z");

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("writes ORDER_CANCELLED to the outbox for trade-events, keyed by account, in the contract's envelope")
    void announces() throws Exception {
        OutboxMapper outbox = mock(OutboxMapper.class);
        UUID orderId = UUID.randomUUID();
        OrderRow order = new OrderRow();
        order.setOrderId(orderId);
        order.setClientId(3L);
        order.setSide(OrderSide.BUY);
        order.setQuantity(new BigDecimal("2"));
        order.setPrice(new BigDecimal("180.00"));

        new OrderCancelledAnnouncer(outbox, json).on(new OrderCancelledDomainEvent(order, "ITC.NS", AT));

        ArgumentCaptor<String> eventId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> envelope = ArgumentCaptor.forClass(String.class);
        verify(outbox).insert(eventId.capture(), eq("trade-events"), eq("3"), envelope.capture());

        JsonNode message = json.readTree(envelope.getValue());
        assertThat(message.get("eventId").asText(), is(eventId.getValue()));
        assertThat(message.get("eventType").asText(), is("ORDER_CANCELLED"));
        assertThat(message.get("eventTime").asText(), is("2026-10-06T10:00:00Z"));
        assertThat(message.get("source").asText(), is("trade-api"));
        assertThat(message.get("schemaVersion").asInt(), is(1));

        JsonNode payload = message.get("payload");
        assertThat(payload.get("orderId").asText(), is(orderId.toString()));
        assertThat(payload.get("accountId").asLong(), is(3L));
        assertThat(payload.get("symbol").asText(), is("ITC.NS"));
        assertThat(payload.get("side").asText(), is("BUY"));
        assertThat(payload.get("quantity").decimalValue(), is(new BigDecimal("2")));
        assertThat(payload.get("status").asText(), is("CANCELLED"));
        assertThat(payload.get("reason").asText(), is("CANCELLED_BY_CUSTOMER"));
        assertThat(payload.get("executedPrice").isNull(), is(true));
        assertThat(payload.get("cashDelta").decimalValue().signum(), is(0));
        assertThat(payload.get("executedOn").asText(), is("2026-10-06T10:00:00Z"));
    }

    @Test
    @DisplayName("every announcement is a new event: its own eventId")
    void eachHasItsOwnEventId() {
        OutboxMapper outbox = mock(OutboxMapper.class);
        OrderRow order = new OrderRow();
        order.setOrderId(UUID.randomUUID());
        order.setClientId(3L);
        order.setSide(OrderSide.SELL);
        order.setQuantity(BigDecimal.ONE);
        order.setPrice(BigDecimal.TEN);
        OrderCancelledAnnouncer announcer = new OrderCancelledAnnouncer(outbox, json);

        announcer.on(new OrderCancelledDomainEvent(order, "ITC.NS", AT));
        announcer.on(new OrderCancelledDomainEvent(order, "ITC.NS", AT));

        ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
        verify(outbox, org.mockito.Mockito.times(2)).insert(ids.capture(), anyString(), anyString(), anyString());
        assertThat(ids.getAllValues().get(0).equals(ids.getAllValues().get(1)), is(false));
    }
}
