package com.yellow.trade.dto;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;

public record OrderResponse(
        String orderId,
        OrderStatus status,
        String message,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price) {
}
