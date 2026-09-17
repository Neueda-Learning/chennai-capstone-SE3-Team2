package com.yellow.executor.fill;

import com.yellow.executor.quotes.Quote;

/**
 * Decides whether an order fills, and at what price.
 *
 * <p>A function of an order and a quote, returning a decision. It touches no
 * database and no socket, and that is not an accident of the design -- it is
 * the only reason the interesting cases actually get tested. A rule that needed
 * a connection to answer "does a BUY limit exactly equal to the ask fill?"
 * would be a rule whose boundaries nobody checks.
 *
 * <p>Implementations are selected by asset class. An instrument class with no
 * two-sided market gets its own rule rather than a special case inside this
 * one, so that adding an instrument class later is a new implementation rather
 * than an edit to a rule that already works.
 */
public interface FillRule {

    /**
     * @param order the order as loaded, with its limit price
     * @param quote the live quote for the order's symbol
     * @return fill at a price, or reject with a reason
     */
    FillDecision decide(OrderSnapshot order, Quote quote);
}
