package com.yellow.trade.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes trade-events in group notification-service: a fill, a rejection
 * and a cancellation each become one notification. It records; it never
 * sends. Values are read as text and parsed here, so a message that fails
 * reaches trade-events.DLT exactly as it arrived.
 */
@Component
class TradeEventsListener {

    private final NotificationLedger ledger;
    private final ObjectMapper json;

    TradeEventsListener(NotificationLedger ledger, ObjectMapper json) {
        this.ledger = ledger;
        this.json = json;
    }

    @KafkaListener(
            id = "notification-service",
            topics = "${notifications.topic:trade-events}",
            groupId = "${notifications.consumer-group:notification-service}",
            containerFactory = "notificationListenerContainerFactory",
            autoStartup = "${notifications.consumer.auto-startup:true}")
    void onTradeEvent(String value) {
        ledger.record(TradeEvent.parse(value, json));
    }
}
