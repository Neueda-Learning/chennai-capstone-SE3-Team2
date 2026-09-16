package com.yellow.trade.security;

//A token was missing, malformed, expired or wrongly signed.

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
