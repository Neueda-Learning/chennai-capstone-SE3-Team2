package com.yellow.trade.advice;

import java.time.Instant;

/**
 * A stated view on one instrument (openapi/advice.yaml): BUY, SELL or HOLD,
 * how strong, what produced it, the numbers behind it, and that it is
 * information, not advice.
 */
public record Signal(String symbol, Direction direction, int strength, String methodology, String reason,
                     SignalFigures figures, Instant computedAt, String disclaimer) {
}
