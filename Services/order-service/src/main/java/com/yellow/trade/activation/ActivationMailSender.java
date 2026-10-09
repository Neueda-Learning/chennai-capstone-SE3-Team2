package com.yellow.trade.activation;

/** How an activation email leaves. Tests use a fake; production uses SMTP. */
public interface ActivationMailSender {

    /** @throws ActivationExceptions.MailDeliveryException when the mail server does not take it */
    void send(ActivationEmail email);
}
