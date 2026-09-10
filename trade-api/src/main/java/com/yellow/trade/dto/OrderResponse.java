package com.yellow.trade.dto;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * contracts/trade-api.yaml -> OrderResponse.
 *
 * orderId is the bare UUID with no display prefix: the value this API hands
 * out is the value DELETE /api/v1/orders/{orderId} accepts back.
 *
 * message is display only. Clients branch on status, or on errorCode when the
 * request failed -- never on this string.
 */
public record OrderResponse(
        UUID orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        OrderStatus status,
        String message,
        Instant placedAt) {
}
