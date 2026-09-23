package com.yellow.executor.fill;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The order as the executor loaded it from Postgres, at the moment it decided.
 * Deliberately not the domain's Order entity.
 */
public record OrderSnapshot(
        UUID orderId,
        Long accountId,
        Long instrumentId,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal limitPrice,
        OrderStatus status) {

    public boolean isBuy() {
        return side == OrderSide.BUY;
    }

    /**
     * True when no previous delivery has already settled this order.
     */
    public boolean isWorking() {
        return status == OrderStatus.NEW;
    }

    /** What the order costs at executedPrice, at the scale the account balance is held in. */
    public BigDecimal considerationAt(BigDecimal executedPrice) {
        return ExecutionPrice.round(quantity.multiply(executedPrice));
    }
}
