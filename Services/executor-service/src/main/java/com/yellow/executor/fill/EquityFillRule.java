package com.yellow.executor.fill;

import com.yellow.executor.quotes.Quote;

import java.math.BigDecimal;

/** The fill rule for instruments with a two-sided market: equities and ETFs. */
public class EquityFillRule implements FillRule {

    @Override
    public FillDecision decide(OrderSnapshot order, Quote quote) {

        // Round before comparing, so that the price the rule accepts and the
        // price the customer is charged are the same number. See ExecutionPrice.
        BigDecimal settlementPrice = ExecutionPrice.round(
                order.isBuy() ? quote.buyPrice() : quote.sellPrice());

        BigDecimal limit = ExecutionPrice.round(order.limitPrice());

        boolean marketable = order.isBuy()
                // A buyer will pay up to their limit. The offer must not exceed it.
                ? limit.compareTo(settlementPrice) >= 0
                // A seller will accept down to their limit. The bid must not fall below it.
                : limit.compareTo(settlementPrice) <= 0;

        return marketable
                ? new FillDecision.Fill(settlementPrice)
                : new FillDecision.Reject(RejectReason.PRICE_NOT_MET);
    }
}
