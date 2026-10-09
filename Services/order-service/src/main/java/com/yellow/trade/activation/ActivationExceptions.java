package com.yellow.trade.activation;

/**
 * The failures the activation mailer tells apart. Transient ones are retried
 * with backoff and then dead-lettered; poison ones are dead-lettered on the
 * first attempt, because no retry will change the answer. Messages carry ids
 * only, never an email address or a token.
 */
public final class ActivationExceptions {

    private ActivationExceptions() {
    }

    /** The message on the topic cannot be read as ACCOUNT_PROVISIONED. Poison. */
    public static class MalformedEventException extends RuntimeException {
        public MalformedEventException(String message) {
            super(message);
        }
    }

    /** No client_profile row, so no address to send to. A real inconsistency. Poison. */
    public static class NoClientProfileException extends RuntimeException {
        public NoClientProfileException(long clientId) {
            super("no client_profile row for client " + clientId);
        }
    }

    /** Auth could not be reached, or failed on its side. Transient. */
    public static class AuthUnavailableException extends RuntimeException {
        public AuthUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Auth refused the request for a reason a retry will not fix. Poison. */
    public static class AuthRefusedException extends RuntimeException {
        public AuthRefusedException(String message) {
            super(message);
        }
    }

    /** The account already has a login: nothing to send. Handled, not an error. */
    public static class AccountAlreadyClaimedException extends RuntimeException {
        public AccountAlreadyClaimedException(long clientId) {
            super("account " + clientId + " already has a login");
        }
    }

    /** The mail server did not take the message. Transient. */
    public static class MailDeliveryException extends RuntimeException {
        public MailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
