package com.yellow.executor.fill;

import com.yellow.executor.quotes.Quote;

import java.math.BigDecimal;

/**
 * The fill rule for instruments with a two-sided market: equities and ETFs.
 *
 * <blockquote>
 * Fill the whole order when a BUY's limit price is at or above the {@code ask},
 * or a SELL's limit price is at or below the {@code bid}. Fill at that same
 * side. Reject otherwise.
 * </blockquote>
 *
 * <p>THE SIDE YOU COMPARE IS THE SIDE YOU STORE. A BUY checked against the bid
 * would fill at a price no seller is offering. So a BUY is compared with the
 * ask and fills at the ask; a SELL is compared with the bid and fills at the
 * bid. One number, used twice.
 *
 * <p>The spread between them is the cost of trading, and charging it is the
 * point. Settling both sides at {@code price} would make a buy followed by a
 * sell cost nothing, and any strategy that trades often would look free when it
 * is not.
 */
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
