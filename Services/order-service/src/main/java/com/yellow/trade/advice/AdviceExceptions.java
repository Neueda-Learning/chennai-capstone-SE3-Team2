package com.yellow.trade.advice;

import com.yellow.exceptions.TradeException;

/** The ways this module refuses. */
public final class AdviceExceptions {

    private AdviceExceptions() {
    }

    /** 422 VAL-422: a fund. It is priced once a day at its NAV; there are no daily candles here to read. */
    public static final class FundSignalException extends TradeException {
        public FundSignalException() {
            super("VAL-422", "A fund has no daily candles to read a signal from");
        }
    }

    /** Something on market-data that can never be a quote. Dead-lettered on the first attempt. */
    public static final class UnreadableQuoteException extends RuntimeException {
        UnreadableQuoteException(String message) {
            super(message);
        }
    }
}
