package com.yellow.executor.fill;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounding, in one place, because the quote carries more decimal places than
 * the column holds.
 *
 * <p>A Fauxnance quote looks like {@code 125102.70693446}. {@code orders.price}
 * and {@code orders.fill_price} are {@code NUMERIC(18,4)}. Something has to
 * give, and the order in which it gives decides whether the rule is sound.
 *
 * <p>ROUND FIRST, THEN COMPARE, THEN STORE THE NUMBER YOU COMPARED. If the
 * comparison runs against the full-precision quote and the stored price is
 * rounded afterwards, an order can fill at a price that failed its own check:
 * a BUY limit of 125102.7069 against an ask of 125102.70693446 fails the
 * comparison, yet the ask rounds down to exactly the limit and would be stored
 * as a price the order should have matched. Rounding once, up front, makes the
 * price the rule saw and the price the customer is charged the same number.
 *
 * <p>HALF_UP rather than HALF_EVEN because it is the convention the rest of the
 * platform already uses -- the domain's money handling and the Sprint 3 columns
 * both round that way -- and a fill priced inconsistently with the balance it
 * debits is the kind of half-paisa discrepancy that shows up in a
 * reconciliation and takes a morning to chase.
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
