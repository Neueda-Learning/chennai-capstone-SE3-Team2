package com.yellow.executor.fill;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The order as the executor loaded it from Postgres, at the moment it decided.
 *
 * <p>Deliberately not the domain's {@code Order} entity. That entity models an
 * order's lifecycle -- it mutates, it enforces transitions, and it rounds money
 * to two places because that is what an order's limit price carries. The
 * executor needs none of that: it reads a row, decides, and writes through a
 * conditional UPDATE. What it does need is four decimal places, because the
 * executed price comes from a quote rather than from a customer.
 *
 * <p>The enums are the domain's, because they are the contract's and there
 * should be exactly one spelling of BUY in this repository.
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

    /**
     * What the order costs at {@code executedPrice}, at the scale the account
     * balance is held in. This is the figure rule 6 is re-checked against --
     * not the notional at the limit price, which is what was checked at
     * acceptance and is no longer the number that matters.
     */
    public BigDecimal considerationAt(BigDecimal executedPrice) {
        return ExecutionPrice.round(quantity.multiply(executedPrice));
    }
}
