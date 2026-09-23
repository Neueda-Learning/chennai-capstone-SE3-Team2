package com.yellow.executor.fill;

import com.yellow.executor.quotes.Quote;

/**
 * The rule for instruments this venue cannot price: mutual funds. A mutual
 * fund has no bid and no ask, and not because our data source is missing them.
 */
public class MutualFundFillRule implements FillRule {

    @Override
    public FillDecision decide(OrderSnapshot order, Quote quote) {
        return new FillDecision.Reject(RejectReason.INSTRUMENT_NOT_PRICEABLE);
    }
}
