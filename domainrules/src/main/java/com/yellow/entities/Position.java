package com.yellow.entities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class Position {

    public static final int QUANTITY_SCALE = 6;

    public static final int AVERAGE_PRICE_SCALE = 4;

    private final Long positionId;
    private final Long accountId;
    private final Long instrumentId;

    private BigDecimal quantity;
    private BigDecimal averagePrice;

    public Position(Long positionId,
                    Long accountId,
                    Long instrumentId,
                    BigDecimal quantity,
                    BigDecimal averagePrice) {

        this.positionId = positionId;   // null until persisted
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");

        requireNotNegative(quantity, "quantity");
        requireNotNegative(averagePrice, "averagePrice");

        this.quantity = scaleQuantity(quantity);
        this.averagePrice = scalePrice(averagePrice);
    }

    public static Position opening(Long accountId,
                                   Long instrumentId,
                                   BigDecimal quantity,
                                   BigDecimal price) {
        requirePositive(quantity, "quantity");
        requirePositive(price, "price");
        return new Position(null, accountId, instrumentId, quantity, price);
    }

    public boolean canSell(BigDecimal requested) {
        requirePositive(requested, "requested");
        return quantity.compareTo(scaleQuantity(requested)) >= 0;
    }

    public boolean isClosed() {
        return quantity.signum() == 0;
    }

    public BigDecimal investedValue() {
        return quantity.multiply(averagePrice);
    }

    public void applyBuy(BigDecimal addedQuantity, BigDecimal price) {
        requirePositive(addedQuantity, "addedQuantity");
        requirePositive(price, "price");

        BigDecimal added = scaleQuantity(addedQuantity);
        BigDecimal newQuantity = quantity.add(added);

        BigDecimal oldCost = quantity.multiply(averagePrice);
        BigDecimal addedCost = added.multiply(price);

        this.averagePrice = oldCost.add(addedCost)
                .divide(newQuantity, AVERAGE_PRICE_SCALE, RoundingMode.HALF_UP);
        this.quantity = newQuantity;
    }

    public void applySell(BigDecimal soldQuantity) {
        requirePositive(soldQuantity, "soldQuantity");
        BigDecimal sold = scaleQuantity(soldQuantity);

        if (quantity.compareTo(sold) < 0) {
            throw new IllegalStateException("sell of " + sold
                    + " refused: holding is " + quantity);
        }
        this.quantity = quantity.subtract(sold);
    }


    public Long positionId()            { return positionId; }
    public Long accountId()             { return accountId; }
    public Long instrumentId()          { return instrumentId; }
    public BigDecimal quantity()        { return quantity; }
    public BigDecimal averagePrice()    { return averagePrice; }

    private static BigDecimal scaleQuantity(BigDecimal value) {
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scalePrice(BigDecimal value) {
        return value.setScale(AVERAGE_PRICE_SCALE, RoundingMode.HALF_UP);
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

    private static void requireNotNegative(BigDecimal amount, String what) {
        if (amount == null) {
            throw new IllegalArgumentException(what + " must not be null");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    what + " must not be negative, was " + amount);
        }
    }

    @Override
    public String toString() {
        return "Position{account=" + accountId + ", instrument=" + instrumentId
                + ", qty=" + quantity + ", avg=" + averagePrice + "}";
    }
}