package com.yellow.executor.quotes;

/**
 * No price could be obtained for a symbol.
 *
 * <p>This is a BUSINESS OUTCOME, not a message-processing failure, and the
 * distinction decides how the message is handled. An order whose quote could
 * not be fetched is rejected with a reason the customer can read. It is never
 * dead-lettered: dead-lettering would take the message off the topic and leave
 * the order at NEW for ever, and a customer watching an order that never
 * resolves is worse served than one told it was rejected.
 *
 * <p>Thrown only once the retry budget inside the client is spent, or
 * immediately for a failure no retry could fix -- an unknown symbol, a rejected
 * key, a malformed request.
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
