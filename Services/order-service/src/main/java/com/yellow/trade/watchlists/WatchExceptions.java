package com.yellow.trade.watchlists;

import com.yellow.exceptions.TradeException;

/** The errors this module adds to the catalogue, answered by WatchlistsExceptionHandler. */
public final class WatchExceptions {

    private WatchExceptions() {
    }

    /** 404 WCH-404: not on this account. Another account's answers the same, so an id reveals nothing. */
    public static final class NotFoundException extends TradeException {
        public static final String CODE = "WCH-404";

        public NotFoundException(String what) {
            super(CODE, what + " not found");
        }
    }

    /** 409 LIM-409: a per-account cap reached. */
    public static final class LimitReachedException extends TradeException {
        public static final String CODE = "LIM-409";

        public LimitReachedException(String message) {
            super(CODE, message);
        }
    }

    /**
     * 422 VAL-422: an alert on a fund. A fund is priced once a day from its NAV
     * and has no quote on market-data, so an alert on one could never fire.
     */
    public static final class FundAlertException extends TradeException {
        public FundAlertException() {
            super("VAL-422", "A fund has no live price to set an alert on");
        }
    }
}
