package com.yellow.trade.activation;

import com.yellow.trade.activation.ActivationExceptions.MailDeliveryException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends through the SMTP server configured under spring.mail (Gmail in development). */
@Component
public class SmtpActivationMailSender implements ActivationMailSender {

    private final JavaMailSender mail;
    private final String from;

    public SmtpActivationMailSender(JavaMailSender mail, ActivationProperties properties) {
        this.mail = mail;
        this.from = properties.mailFrom();
    }

    @Override
    public void send(ActivationEmail email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.to());
        message.setSubject(ActivationEmail.SUBJECT);
        message.setText(email.body());
        try {
            mail.send(message);
        } catch (MailException e) {
            // The exception's own message can quote the recipient, so it is not
            // repeated here; the cause is kept for the error handler's class check.
            throw new MailDeliveryException("mail server did not accept the activation email", e);
        }
    }
}
