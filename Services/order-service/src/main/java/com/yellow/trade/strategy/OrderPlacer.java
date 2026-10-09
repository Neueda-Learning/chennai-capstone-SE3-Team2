package com.yellow.trade.strategy;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Places a strategy's order. Always through POST /api/v1/orders, never on the
 * orders topic and never by calling the order service: the route's
 * validation, authorisation and idempotency stand between a strategy bug and
 * a position.
 */
public interface OrderPlacer {

    record Result(boolean placed, UUID orderId, String reason) {
        static Result placed(UUID orderId) {
            return new Result(true, orderId, null);
        }

        static Result failed(String reason) {
            return new Result(false, null, reason);
        }
    }

    Result place(long accountId, String symbol, String side, int quantity, BigDecimal limitPrice, String idempotencyKey);
}
