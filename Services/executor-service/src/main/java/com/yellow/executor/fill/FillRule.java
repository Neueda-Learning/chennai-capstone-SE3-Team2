package com.yellow.executor.fill;

import com.yellow.executor.quotes.Quote;

/**
 * Decides whether an order fills, and at what price. A function of an order
 * and a quote, returning a decision.
 */
public interface FillRule {

    FillDecision decide(OrderSnapshot order, Quote quote);
}
