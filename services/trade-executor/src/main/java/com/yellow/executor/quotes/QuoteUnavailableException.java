package com.yellow.executor.quotes;

/**
 * No price could be obtained for a symbol. This is a BUSINESS OUTCOME, not a
 * message-processing failure, and the distinction decides how the message is
 * handled.
 */
public class QuoteUnavailableException extends RuntimeException {

    private final String symbol;
    private final int attempts;

    public QuoteUnavailableException(String message, String symbol, int attempts) {
        super(message);
        this.symbol = symbol;
        this.attempts = attempts;
    }

    public QuoteUnavailableException(String message, String symbol, int attempts, Throwable cause) {
        super(message, cause);
        this.symbol = symbol;
        this.attempts = attempts;
    }

    public String symbol() {
        return symbol;
    }

    /** How many times we asked before giving up. Goes in the log, not the event. */
    public int attempts() {
        return attempts;
    }
}
