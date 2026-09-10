package com.yellow.trade;

import java.util.UUID;

/**
 * The two forms of an order identifier, and the only place they convert.
 *
 * The contract stores a UUID and displays it with an ORD- prefix. Both forms
 * appear in the same API: responses carry the prefixed string, and
 * DELETE /api/v1/orders/{id} takes the bare UUID, "without the ORD- display
 * prefix".
 *
 * Two conversions written in two places is how the value an API hands out
 * stops being the value it accepts back, so both live here.
 */
public final class OrderIdentifier {

    private static final String PREFIX = "ORD-";

    private OrderIdentifier() {
    }

    /** The stored UUID as the contract displays it. */
    public static String display(UUID orderId) {
        return orderId == null ? null : PREFIX + orderId;
    }
}
