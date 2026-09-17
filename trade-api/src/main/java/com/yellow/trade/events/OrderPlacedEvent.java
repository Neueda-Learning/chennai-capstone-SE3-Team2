package com.yellow.trade.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yellow.enums.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kafka payload for ORDER_PLACED events.
 * Published to the 'orders' topic, keyed by account ID, after the order transaction commits.
 */
public record OrderPlacedEvent(
        @JsonProperty("orderId")
        String orderId,

        @JsonProperty("accountId")
        Long accountId,

        @JsonProperty("symbol")
        String symbol,

        @JsonProperty("side")
        OrderSide side,

        @JsonProperty("quantity")
        BigDecimal quantity,

        @JsonProperty("status")
        String status,

        @JsonProperty("timestamp")
        Instant timestamp,

        @JsonProperty("idempotencyKey")
        String idempotencyKey,

        @JsonProperty("instrumentId")
        Long instrumentId) {
}
