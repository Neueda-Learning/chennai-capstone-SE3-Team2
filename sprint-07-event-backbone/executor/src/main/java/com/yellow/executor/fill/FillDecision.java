package com.yellow.executor.fill;

import java.math.BigDecimal;

/**
 * What the executor decided about one order. Sealed, so that handling a
 * decision is a switch the compiler checks rather than a pair of nullable
 * fields nobody can be sure about.
 *
 * <p>There is no partial fill. The order status enumeration has no state to
 * represent one and there is no working state between NEW and a terminal
 * status, so an unmarketable order is rejected rather than rested.
 */
public sealed interface FillDecision {

    /**
     * Fill the whole order at {@code executedPrice}.
     *
     * <p>{@code executedPrice} is already rounded to the width of
     * {@code orders.fill_price}. It is the same number the rule compared the
     * limit price against -- rounding after the comparison would let an order
     * fill at a price that failed its own check.
     */
    record Fill(BigDecimal executedPrice) implements FillDecision {

        public Fill {
            if (executedPrice == null || executedPrice.signum() <= 0) {
                throw new IllegalArgumentException(
                        "a fill needs a positive executed price, was " + executedPrice);
            }
        }
    }

    /**
     * Reject the order, terminally, with a reason the customer and the
     * analytics estate can both read.
     */
    record Reject(RejectReason reason) implements FillDecision {

        public Reject {
            if (reason == null) {
                throw new IllegalArgumentException("a rejection needs a reason");
            }
        }
    }

    default boolean isFill() {
        return this instanceof Fill;
    }
}
