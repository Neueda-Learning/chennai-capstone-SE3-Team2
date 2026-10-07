package com.yellow.trade.notifications;

/** How a notification leaves by email. Tests use a fake; the platform uses its SMTP server. */
public interface NotificationMailSender {

    /** @throws NotificationExceptions.MailDeliveryException when the mail server does not take it */
    void send(String to, String subject, String body);
}
