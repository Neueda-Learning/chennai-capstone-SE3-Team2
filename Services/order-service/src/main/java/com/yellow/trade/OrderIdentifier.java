package com.yellow.trade;

import java.util.UUID;
public final class OrderIdentifier {

    private static final String PREFIX = "ORD-";

    private OrderIdentifier() {
    }
    public static String display(UUID orderId) {
        return orderId == null ? null : PREFIX + orderId;
    }
}
