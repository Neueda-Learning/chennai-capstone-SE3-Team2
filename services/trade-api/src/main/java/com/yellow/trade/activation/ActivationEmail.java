package com.yellow.trade.activation;

/**
 * One activation email: who it goes to, and the link that carries the token.
 * Both are secrets as far as logging is concerned, so toString prints neither.
 */
public record ActivationEmail(String to, String link) {

    public static final String SUBJECT = "Activate your trading account";

    public String body() {
        return """
                Your trading account is ready.

                Open this link to choose a username and password:

                %s

                The link works once and expires in 24 hours. If you did not expect
                this email, you can ignore it: nothing happens until the link is used.
                """.formatted(link);
    }

    @Override
    public String toString() {
        return "ActivationEmail[redacted]";
    }
}
