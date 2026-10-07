package com.yellow.trade.strategy;

import com.yellow.exceptions.TradeException;

/** The errors this module adds to the catalogue, answered by StrategyExceptionHandler. */
public final class StrategyExceptions {

    private StrategyExceptions() {
    }

    /** 404 STR-404: no such strategy on this account. Another account's answers the same. */
    public static final class NotFoundException extends TradeException {
        public static final String CODE = "STR-404";

        public NotFoundException() {
            super(CODE, "Strategy not found");
        }
    }

    /** 409 LIM-409: ten strategies an account. */
    public static final class LimitReachedException extends TradeException {
        public static final String CODE = "LIM-409";

        public LimitReachedException() {
            super(CODE, "At most " + StrategyService.MAX_STRATEGIES + " strategies an account");
        }
    }

    /** 422 VAL-422: a fund has no quote on market-data, so a strategy on one could never fire. */
    public static final class FundStrategyException extends TradeException {
        public FundStrategyException() {
            super("VAL-422", "A fund has no live price for a strategy to watch");
        }
    }

    /** A message that can never be read. Dead-lettered on the first attempt. */
    public static final class UnreadableEventException extends RuntimeException {
        public UnreadableEventException(String message) {
            super(message);
        }
    }
}
