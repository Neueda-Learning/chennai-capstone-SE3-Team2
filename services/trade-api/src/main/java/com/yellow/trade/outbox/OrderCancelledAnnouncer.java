package com.yellow.trade.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.trade.config.KafkaTopics;
import com.yellow.trade.events.EventEnvelope;
import com.yellow.trade.events.OrderCancelledDomainEvent;
import com.yellow.trade.events.TradeEventPayload;
import com.yellow.trade.mappers.OrderRow;
import com.yellow.trade.mappers.OutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puts ORDER_CANCELLED on trade-events, through the outbox. Until Sprint 10
 * only the executor published there, fills and rejections, so a cancellation
 * reached no consumer.
 *
 * A plain @EventListener, so it runs inside the cancel's transaction: the
 * outbox row commits with the cancel, and OutboxRelay publishes it after.
 * Same payload as the executor's: no executed price, nothing moved in cash
 * (the reservation was released, the balance never changed), and the
 * position fields null, as on a rejection, since the position did not move.
 */
@Component
public class OrderCancelledAnnouncer {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledAnnouncer.class);

    static final String EVENT_TYPE = "ORDER_CANCELLED";
    static final String REASON = "CANCELLED_BY_CUSTOMER";
    private static final String SOURCE = "trade-api";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxMapper outbox;
    private final ObjectMapper json;

    public OrderCancelledAnnouncer(OutboxMapper outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @EventListener
    public void on(OrderCancelledDomainEvent cancelled) {
        OrderRow order = cancelled.order();
        TradeEventPayload payload = new TradeEventPayload(
                order.getOrderId().toString(),
                order.getClientId(),
                cancelled.symbol(),
                order.getSide().name(),
                order.getQuantity(),
                order.getPrice(),
                null,
                "CANCELLED",
                REASON,
                BigDecimal.ZERO,
                null,
                null,
                cancelled.cancelledAt());
        String eventId = UUID.randomUUID().toString();
        EventEnvelope<TradeEventPayload> envelope = new EventEnvelope<>(
                eventId, EVENT_TYPE, cancelled.cancelledAt(), SOURCE, SCHEMA_VERSION, payload);

        outbox.insert(eventId, KafkaTopics.TRADE_EVENTS, order.getClientId().toString(), write(envelope));
        log.info("ORDER_CANCELLED {} queued in the outbox for order {} on account {}",
                eventId, order.getOrderId(), order.getClientId());
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not write ORDER_CANCELLED", e);
        }
    }
}
