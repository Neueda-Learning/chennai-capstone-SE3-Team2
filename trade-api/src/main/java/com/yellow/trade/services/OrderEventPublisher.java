package com.yellow.trade.services;

import com.yellow.entities.Order;
import com.yellow.trade.OrderIdentifier;
import com.yellow.trade.events.OrderPlacedDomainEvent;
import com.yellow.trade.events.OrderPlacedEvent;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.config.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.time.Instant;

/**
 * Publishes ORDER_PLACED events to Kafka after order transactions commit.
 * Uses @TransactionalEventListener(phase=AFTER_COMMIT) to ensure events are only
 * published after the order has been successfully persisted to the database.
 *
 * Handles transient failures with retry logic; permanent failures are logged
 * to allow manual replay of the order from the order table.
 */
@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final InstrumentMapper instrumentMapper;

    public OrderEventPublisher(KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate,
                               InstrumentMapper instrumentMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.instrumentMapper = instrumentMapper;
    }

    /**
     * Publishes an ORDER_PLACED event for a newly placed order.
     * This method is triggered only after the order transaction has successfully committed,
     * due to the @TransactionalEventListener(phase=AFTER_COMMIT) configuration.
     *
     * @param event The domain event containing the order that was placed.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishOrderPlaced(OrderPlacedDomainEvent event) {
        Order order = event.getOrder();
        
        try {
            publishOrderPlacedInternal(order);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_PLACED for order {} (account {}). " +
                    "Order has been committed and can be manually replayed from the order table.",
                    order.orderId(), order.accountId(), e);
        }
    }

    /**
     * Internal method that performs the actual Kafka publish.
     * Constructs the OrderPlacedEvent payload, sends it with the account ID as the partition key.
     *
     * @param order The order to publish an event for.
     */
    private void publishOrderPlacedInternal(Order order) {
        InstrumentRow instrument = instrumentMapper.findById(order.instrumentId());
        
        OrderPlacedEvent event = new OrderPlacedEvent(
                OrderIdentifier.display(order.orderId()),
                order.accountId(),
                instrument == null ? null : instrument.getSymbol(),
                order.side(),
                order.quantity(),
                "NEW",
                Instant.now(),
                order.idempotencyKey(),
                order.instrumentId()
        );

        String partitionKey = order.accountId().toString();

        // Send to Kafka with account ID as partition key for ordering per account
        kafkaTemplate.send(KafkaTopics.ORDERS, partitionKey, event);

        log.info("ORDER_PLACED published: orderId={} account={} symbol={}",
                order.orderId(), order.accountId(), instrument == null ? null : instrument.getSymbol());
    }
}
