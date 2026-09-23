package com.yellow.executor.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yellow.enums.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The ORDER_PLACED payload, as the Trade REST API publishes it. UNKNOWN FIELDS
 * ARE IGNORED, deliberately and at the type as well as in the deserialiser.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedPayload(
        String orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        String status,
        Instant createdOn,
        String idempotencyKey,
        Long instrumentId) {
}
