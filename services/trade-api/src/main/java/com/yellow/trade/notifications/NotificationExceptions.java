package com.yellow.trade.notifications;

/** The ways this module's consumer and dispatcher fail, kept apart so the error handler can tell them apart. */
public final class NotificationExceptions {

    private NotificationExceptions() {
    }

    /**
     * A trade-events message that can never become a notification: not JSON,
     * a type the topic does not carry, or a field missing. Dead-lettered on the
     * first attempt; retrying it changes nothing. The message names the field,
     * never a value.
     */
    public static final class UnreadableEventException extends RuntimeException {
        public UnreadableEventException(String message) {
            super(message);
        }

        public UnreadableEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The mail server did not take a message. Its own error can quote the recipient, so it is not repeated. */
    public static final class MailDeliveryException extends RuntimeException {
        public MailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
