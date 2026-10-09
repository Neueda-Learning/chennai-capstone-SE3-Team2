package com.yellow.trade.activation;

import com.yellow.trade.activation.ActivationExceptions.AccountAlreadyClaimedException;
import com.yellow.trade.activation.ActivationExceptions.NoClientProfileException;
import com.yellow.trade.mappers.ActivationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * One ACCOUNT_PROVISIONED event in, at most one activation email out.
 *
 * Idempotent on eventId: a redelivered event finds its row in activation_email
 * and sends nothing. The row is written after the send, so a crash between the
 * two resends on redelivery; that is the at-least-once trade the platform
 * makes everywhere, and a second email is recoverable where a lost one is not.
 *
 * Logs name the event and the client. Never the email address, never the token.
 */
@Service
public class ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationService.class);

    private final ActivationMapper mapper;
    private final AuthTokenClient auth;
    private final ActivationMailSender mail;
    private final String linkBaseUrl;

    public ActivationService(ActivationMapper mapper, AuthTokenClient auth,
                             ActivationMailSender mail, ActivationProperties properties) {
        this.mapper = mapper;
        this.auth = auth;
        this.mail = mail;
        this.linkBaseUrl = properties.linkBaseUrl();
    }

    public void handle(AccountProvisionedEvent event) {
        UUID eventId = UUID.fromString(event.eventId());
        long clientId = event.clientId();

        if (mapper.countSent(eventId) > 0) {
            log.info("activation event {} for client {} already emailed; nothing sent", eventId, clientId);
            return;
        }

        // The address first: a token minted for a client with nowhere to send
        // it would sit unused, and would revoke any earlier live link.
        String email = mapper.findEmail(clientId);
        if (email == null || email.isBlank()) {
            throw new NoClientProfileException(clientId);
        }

        String token;
        try {
            token = auth.mintToken(clientId);
        } catch (AccountAlreadyClaimedException e) {
            log.info("activation event {}: client {} already has a login; nothing sent", eventId, clientId);
            return;
        }

        mail.send(new ActivationEmail(email, link(token)));
        mapper.recordSent(eventId, clientId);

        log.info("activation email sent for event {} to client {}", eventId, clientId);
    }

    /** The token and nothing else identifying: no client id, no email. */
    String link(String token) {
        return linkBaseUrl + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
