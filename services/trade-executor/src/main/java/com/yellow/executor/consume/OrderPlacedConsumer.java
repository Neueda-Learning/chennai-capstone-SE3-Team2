package com.yellow.executor.consume;

import com.yellow.executor.events.EventEnvelope;
import com.yellow.executor.events.OrderPlacedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Reads accepted orders off orders and hands each to the executor. */
@Component
public class OrderPlacedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedConsumer.class);

    private static final String ORDER_PLACED = "ORDER_PLACED";

    private final OrderExecutionService executor;

    public OrderPlacedConsumer(OrderExecutionService executor) {
        this.executor = executor;
    }

    @KafkaListener(
            topics = "${executor.topics.orders:orders}",
            groupId = "${executor.consumer-group:trade-executor}",
            containerFactory = "orderPlacedListenerContainerFactory")
    public void onOrderPlaced(EventEnvelope<OrderPlacedPayload> envelope,
                              @Header(KafkaHeaders.RECEIVED_KEY) String key,
                              @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                              @Header(KafkaHeaders.OFFSET) long offset,
                              Acknowledgment ack) {

        // An event type we do not recognise is a poison message: story 613
        // dead-letters it rather than retrying, because a message whose
        if (!ORDER_PLACED.equals(envelope.eventType())) {
            throw new UnexpectedEventTypeException(envelope.eventType());
        }

        OrderPlacedPayload payload = envelope.payload();
        if (payload == null || payload.orderId() == null) {
            throw new UnexpectedEventTypeException("ORDER_PLACED with no orderId");
        }

        UUID orderId = parse(payload.orderId());

        log.debug("consumed {} for order {} on account {} (partition {}, offset {}, key {})",
                envelope.eventType(), orderId, payload.accountId(), partition, offset, key);

        executor.execute(orderId);

        // After the work, never before. Story 611 moves the publish in between:
        // settle, publish, then acknowledge.
        ack.acknowledge();
    }

    private static UUID parse(String orderId) {
        try {
            return UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            // The contract says orderId is a bare UUID matching orders.order_id.
            // Anything else is malformed and will never parse.
            throw new UnexpectedEventTypeException("orderId '" + orderId + "' is not a UUID");
        }
    }
}
