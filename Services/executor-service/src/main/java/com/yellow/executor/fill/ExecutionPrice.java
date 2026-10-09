package com.yellow.executor.fill;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounding, in one place, because the quote carries more decimal places than
 * the column holds. 70693446.
 */
public final class ExecutionPrice {

    /** The width of orders.price and orders.fill_price: NUMERIC(18,4). */
    public static final int SCALE = 4;

    private ExecutionPrice() {
    }

    /**
     * Round a quoted price to the width of the column that will hold it.
     */
    public static BigDecimal round(BigDecimal quoted) {
        if (quoted == null) {
            throw new IllegalArgumentException("no price to round");
        }
        return quoted.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
