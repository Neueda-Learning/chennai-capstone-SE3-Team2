package com.yellow.trade.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yellow.enums.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

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

        @JsonProperty("price")
        BigDecimal price,

        @JsonProperty("status")
        String status,

        @JsonProperty("createdOn")
        Instant createdOn,

        @JsonProperty("idempotencyKey")
        String idempotencyKey,

        @JsonProperty("instrumentId")
        Long instrumentId) {
}

