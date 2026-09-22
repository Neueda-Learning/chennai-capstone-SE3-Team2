package com.yellow.trade.dto;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Historical order record from account order query")
public record OrderHistoryEntry(
        @Schema(description = "Order identifier (ORD-UUID format)", example = "ORD-550e8400-e29b-41d4-a716-446655440000")
        String orderId,
        @Schema(description = "Account ID", example = "1")
        Long accountId,
        @Schema(description = "Instrument symbol", example = "AAPL")
        String symbol,
        @Schema(description = "Order side (BUY or SELL)")
        OrderSide side,
        @Schema(description = "Order quantity", example = "100")
        BigDecimal quantity,
        @Schema(description = "Order limit price", example = "150.00")
        BigDecimal price,
        @Schema(description = "Price at which order was executed (null if not filled)", example = "150.00")
        BigDecimal executedPrice,
        @Schema(description = "Current order status")
        OrderStatus status,
        @Schema(description = "Idempotency key for order placement", example = "unique-key-12345")
        String idempotencyKey,
        @Schema(description = "Timestamp when order was created")
        Instant createdOn) {
}
