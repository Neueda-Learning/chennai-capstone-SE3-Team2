package com.yellow.trade.advice;

/** One stock an account holds or watches, and its signal (openapi/advice.yaml). */
public record AdviceItem(String symbol, boolean held, boolean watched, Signal signal) {
}
