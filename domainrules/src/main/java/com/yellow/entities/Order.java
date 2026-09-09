package com.yellow.entities;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
public class Order {

    private static final int MONEY_SCALE = 2;
    private static final int QUANTITY_SCALE = 6;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.NEW,       EnumSet.of(OrderStatus.FILLED,
                                              OrderStatus.REJECTED,
                                              OrderStatus.CANCELLED),
            OrderStatus.FILLED,    Collections.emptySet(),
            OrderStatus.REJECTED,  Collections.emptySet(),
            OrderStatus.CANCELLED, Collections.emptySet()
    );

    private final Long orderId;
    private final Long accountId;
    private final Long instrumentId;
    private final OrderSide side;
    private final BigDecimal quantity;

    private final BigDecimal limitPrice;

    private BigDecimal executedPrice;

    private OrderStatus status;
    private final String idempotencyKey;
    private final Instant placedAt;

    private Instant resolvedAt;

    public Order(Long orderId,
                 Long accountId,
                 Long instrumentId,
                 OrderSide side,
                 BigDecimal quantity,
                 BigDecimal limitPrice,
                 BigDecimal executedPrice,
                 OrderStatus status,
                 String idempotencyKey,
                 Instant placedAt,
                 Instant resolvedAt) {

        this.orderId = orderId;
        this.accountId = java.util.Objects.requireNonNull(accountId, "accountId");
        this.instrumentId =
                java.util.Objects.requireNonNull(instrumentId, "instrumentId");
        this.side = java.util.Objects.requireNonNull(side, "side");
        this.status = java.util.Objects.requireNonNull(status, "status");
        this.placedAt = java.util.Objects.requireNonNull(placedAt, "placedAt");

        requirePositive(quantity, "quantity");
        requirePositive(limitPrice, "limitPrice");

        this.quantity = quantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        this.limitPrice = money(limitPrice);
        this.executedPrice = executedPrice == null ? null : money(executedPrice);
        this.idempotencyKey = idempotencyKey;
        this.resolvedAt = resolvedAt;

        if (status == OrderStatus.NEW && resolvedAt != null) {
            throw new IllegalArgumentException("a NEW order has no resolvedAt");
        }
        if (status != OrderStatus.NEW && resolvedAt == null) {
            throw new IllegalArgumentException(
                    "a " + status + " order must carry resolvedAt");
        }
        if (status == OrderStatus.FILLED && this.executedPrice == null) {
            throw new IllegalArgumentException(
                    "a FILLED order must carry an executed price");
        }
    }

    public static Order place(Long accountId,
                              Long instrumentId,
                              OrderSide side,
                              BigDecimal quantity,
                              BigDecimal limitPrice,
                              String idempotencyKey) {
        return new Order(null, accountId, instrumentId, side, quantity,
                limitPrice, null, OrderStatus.NEW, idempotencyKey,
                Instant.now(), null);
    }

    public void transitionTo(OrderStatus next, Instant at) {
        java.util.Objects.requireNonNull(next, "next");
        java.util.Objects.requireNonNull(at, "at");

        if (!ALLOWED.get(status).contains(next)) {
            throw new IllegalStateException("cannot transition from " + status
                    + " to " + next
                    + (isTerminal() ? ": " + status + " is terminal" : ""));
        }
        this.status = next;
        this.resolvedAt = at;
    }

    public void fill(BigDecimal priceAchieved, Instant at) {
        requirePositive(priceAchieved, "priceAchieved");
        transitionTo(OrderStatus.FILLED, at);
        this.executedPrice = money(priceAchieved);
    }

    public void reject(Instant at) {
        transitionTo(OrderStatus.REJECTED, at);
    }

    public void cancel(Instant at) {
        transitionTo(OrderStatus.CANCELLED, at);
    }

    public boolean isTerminal() {
        return ALLOWED.get(status).isEmpty();
    }

    public boolean isBuy() {
        return side == OrderSide.BUY;
    }

    /** What the order would cost at the submitted limit price. */
    public BigDecimal notionalValue() {
        return money(quantity.multiply(limitPrice));
    }

    public Long orderId()               { return orderId; }
    public Long accountId()             { return accountId; }
    public Long instrumentId()          { return instrumentId; }
    public OrderSide side()             { return side; }
    public BigDecimal quantity()        { return quantity; }
    public BigDecimal limitPrice()      { return limitPrice; }
    public BigDecimal executedPrice()   { return executedPrice; }
    public OrderStatus status()         { return status; }
    public String idempotencyKey()      { return idempotencyKey; }
    public Instant placedAt()           { return placedAt; }
    public Instant resolvedAt()         { return resolvedAt; }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static void requirePositive(BigDecimal amount, String what) {
        if (amount == null) {
            throw new IllegalArgumentException(what + " must not be null");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    what + " must be positive, was " + amount);
        }
    }

    @Override
    public String toString() {
        return "Order{" + side + " " + quantity + " of instrument "
                + instrumentId + " @ " + limitPrice + ", " + status + "}";
    }


}