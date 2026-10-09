package com.yellow.executor.fill;

import java.math.BigDecimal;

/**
 * What the executor decided about one order. Sealed, so that handling a
 * decision is a switch the compiler checks rather than a pair of nullable
 * fields nobody can be sure about.
 */
public sealed interface FillDecision {

    /** Fill the whole order at executedPrice. fill_price. */
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
