package com.yellow.executor.fill;

import com.yellow.executor.quotes.Quote;

/**
 * The rule for instruments this venue cannot price: mutual funds.
 *
 * <p>A mutual fund has no bid and no ask, and not because our data source is
 * missing them. It does not trade on an order book. Units are allotted at a NAV
 * struck once, after close, from the value of the fund's holdings -- there is
 * no counterparty quoting a two-sided price, so {@link EquityFillRule}'s
 * comparison has nothing to evaluate.
 *
 * <p>Three further things would have to change before an MF order could execute
 * here, and all three are fixed by contracts this sprint does not touch: the
 * quantity would have to be fractional, because an allotment is money divided
 * by NAV and {@code PlaceOrderRequest.quantity} is {@code int32}; the order
 * would need a state between NEW and terminal, because allotment confirms a day
 * later and {@code OrderStatus} has four values; and a NAV source would have to
 * exist. A price feed alone would not be enough.
 *
 * <p>So the order is rejected, terminally and on purpose, rather than left at
 * NEW for a price that is never coming. The reason says permanent, because it
 * is: {@link RejectReason#INSTRUMENT_NOT_PRICEABLE} rather than
 * {@link RejectReason#NO_PRICE}.
 *
 * <p>This class exists rather than an {@code if} inside the equity rule so that
 * the shape of the answer is visible: pricing is per asset class, and a class
 * we cannot price says so in one place.
 */
public class MutualFundFillRule implements FillRule {

    @Override
    public FillDecision decide(OrderSnapshot order, Quote quote) {
        return new FillDecision.Reject(RejectReason.INSTRUMENT_NOT_PRICEABLE);
    }
}
