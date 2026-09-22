package com.yellow.trade.dto;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Order submission response or cancellation confirmation")
public record OrderResponse(
        @Schema(description = "Order identifier (ORD-UUID format)", example = "ORD-550e8400-e29b-41d4-a716-446655440000")
        String orderId,
        @Schema(description = "Current order status")
        OrderStatus status,
        @Schema(description = "Status message or error description", example = "Order placed successfully")
        String message,
        @Schema(description = "Instrument symbol", example = "AAPL")
        String symbol,
        @Schema(description = "Order side (BUY or SELL)")
        OrderSide side,
        @Schema(description = "Order quantity", example = "100")
        BigDecimal quantity,
        @Schema(description = "Order limit price", example = "150.00")
        BigDecimal price) {
}
