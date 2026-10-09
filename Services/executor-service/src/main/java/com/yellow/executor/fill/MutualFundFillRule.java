package com.yellow.executor.fill;

import com.yellow.executor.quotes.Quote;

import java.math.BigDecimal;

/**
 * The rule for mutual funds. A fund has no bid and no ask: it deals at its
 * NAV, one price both ways, published once a day by AMFI and served by the MF
 * NAV service. The limit is honoured as it is for a stock -- a BUY fills when
 * its limit is at or above the NAV, a SELL when at or below.
 */
public class MutualFundFillRule implements FillRule {

    @Override
    public FillDecision decide(OrderSnapshot order, Quote quote) {
        // Rounded before comparing, so the NAV the rule accepts and the price
        // the customer is charged are the same number. See ExecutionPrice.
        BigDecimal nav = ExecutionPrice.round(quote.price());
        BigDecimal limit = ExecutionPrice.round(order.limitPrice());

        boolean marketable = order.isBuy()
                ? limit.compareTo(nav) >= 0
                : limit.compareTo(nav) <= 0;

        return marketable
                ? new FillDecision.Fill(nav)
                : new FillDecision.Reject(RejectReason.PRICE_NOT_MET);
    }
}
