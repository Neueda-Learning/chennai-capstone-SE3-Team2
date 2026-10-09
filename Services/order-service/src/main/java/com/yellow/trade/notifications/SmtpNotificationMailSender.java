package com.yellow.trade.notifications;

import com.yellow.trade.notifications.NotificationExceptions.MailDeliveryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends through the platform's SMTP server, the one configured under
 * spring.mail for the activation emails (Gmail in development), from the same
 * sender unless notifications.mail-from says otherwise.
 */
@Component
class SmtpNotificationMailSender implements NotificationMailSender {

    static final String FOOTER = """


            You get these by email because email is your alert channel. You can change it in Settings, \
            and every notification is also under the bell in the app.""";

    private final JavaMailSender mail;
    private final String from;

    SmtpNotificationMailSender(JavaMailSender mail,
                               @Value("${notifications.mail-from:${activation.mail-from}}") String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body + FOOTER);
        try {
            mail.send(message);
        } catch (MailException e) {
            // Its own message can quote the recipient, so it is not repeated.
            throw new MailDeliveryException("mail server did not accept the message", e);
        }
    }
}
