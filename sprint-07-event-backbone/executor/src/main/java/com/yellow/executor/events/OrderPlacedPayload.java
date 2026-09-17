package com.yellow.executor.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yellow.enums.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The ORDER_PLACED payload, as the Trade REST API publishes it.
 *
 * <p>UNKNOWN FIELDS ARE IGNORED, deliberately and at the type as well as in the
 * deserialiser. Adding an optional field is not a breaking change under the
 * contract, so a consumer that threw on one would turn somebody else's additive
 * release into an outage across every account on this topic. Note this is the
 * OPPOSITE of the Trade REST API's HTTP setting, which fails on unknown
 * properties because its OpenAPI contract declares additionalProperties false.
 * Different boundary, different rule.
 *
 * <p>{@code price} is the LIMIT price the customer named, not a fill price.
 * Nothing on this topic has been priced yet -- that is the executor's job.
 *
 * <p>{@code instrumentId} arrives on the wire but nothing here branches on it:
 * it is the producer's internal key, and the executor resolves the instrument
 * from the order row it loads. {@code status} is always NEW for the same
 * reason -- the topic carries accepted orders, and the database is the
 * authority on what an order's status is now.
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
