package com.yellow.trade.security;

/**
 * A token was missing, malformed, expired or wrongly signed.
 *
 * One type for all four, and the reason is carried on a typed field for the
 * server log only. The client gets the same AUTH-401 body whichever it was:
 * telling an attacker which of the four they got is telling them what to fix.
 */
public class TokenVerificationException extends RuntimeException {

    public enum Reason { MISSING, MALFORMED, EXPIRED, BAD_SIGNATURE, BAD_ALGORITHM, WRONG_ISSUER, NO_ACCOUNT_CLAIM }

    private final transient Reason reason;

    public TokenVerificationException(Reason reason, String logDetail) {
        super(logDetail);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
