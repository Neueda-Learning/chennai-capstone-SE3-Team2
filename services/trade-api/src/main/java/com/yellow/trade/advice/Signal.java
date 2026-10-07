package com.yellow.trade.advice;

import java.time.Instant;

/**
 * A stated view on one instrument (openapi/advice.yaml): BUY, SELL or HOLD,
 * how strong, what produced it, the numbers behind it, and that it is
 * information, not advice. With too little data for a view, no signal at
 * all: direction and strength null, and the reason says what is missing.
 */
public record Signal(String symbol, Direction direction, Integer strength, String methodology, String reason,
                     SignalFigures figures, Instant computedAt, String disclaimer) {
}
