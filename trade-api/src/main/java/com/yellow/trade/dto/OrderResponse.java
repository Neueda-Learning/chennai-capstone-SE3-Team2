package com.yellow.trade.dto;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;

/**
 * contracts/trade-api.yaml -> OrderResponse.
 *
 * Seven fields, and no more: the schema declares additionalProperties: false,
 * so an account key or a timestamp added here would fail a client that
 * validates. The account is not returned because the caller supplied it and
 * the token already proved it.
 *
 * orderId carries the ORD- display prefix over the stored UUID, which is what
 * the contract shows in every example. DELETE /api/v1/orders/{id} takes the
 * bare UUID -- the prefix is for display, and OrderIdentifier owns the
 * conversion in one place so the two cannot drift.
 *
 * message is for a human reading a screen. Never branch on it: branch on
 * status, or on errorCode when the request failed.
 */
public record OrderResponse(
        String orderId,
        OrderStatus status,
        String message,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price) {
}
