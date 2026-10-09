package com.yellow.trade.activation;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.yellow.trade.activation.ActivationExceptions.AccountAlreadyClaimedException;
import com.yellow.trade.activation.ActivationExceptions.AuthUnavailableException;
import com.yellow.trade.activation.ActivationExceptions.MailDeliveryException;
import com.yellow.trade.activation.ActivationExceptions.NoClientProfileException;
import com.yellow.trade.mappers.ActivationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivationServiceTest {

    private static final String EMAIL = "priya.menon@example.com";
    private static final String TOKEN = "9c1f7a2e4b6d8e0a2c4e6a8c0e2a4c6e8a0c2e4a6c8e0a2c4e6a8c0e2a4c6e8a";
    private static final String LINK_BASE = "http://localhost:3000/activate";

    /** The trading database's two tables, as far as the mailer can see them. */
    static final class InMemoryActivationMapper implements ActivationMapper {
        final Map<Long, String> emails = new HashMap<>();
        final Set<UUID> sent = new HashSet<>();

        @Override public String findEmail(long clientId) { return emails.get(clientId); }
        @Override public int countSent(UUID eventId) { return sent.contains(eventId) ? 1 : 0; }
        @Override public int recordSent(UUID eventId, long clientId) { return sent.add(eventId) ? 1 : 0; }
    }

    /** Records what would have been sent. Sends nothing. */
    static final class FakeMailSender implements ActivationMailSender {
        final List<ActivationEmail> outbox = new ArrayList<>();
        RuntimeException failWith;

        @Override
        public void send(ActivationEmail email) {
            if (failWith != null) throw failWith;
            outbox.add(email);
        }
    }

    private InMemoryActivationMapper mapper;
    private FakeMailSender mail;
    private AuthTokenClient auth;
    private ActivationService service;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void wire() {
        mapper = new InMemoryActivationMapper();
        mapper.emails.put(7L, EMAIL);
        mail = new FakeMailSender();
        auth = mock(AuthTokenClient.class);
        when(auth.mintToken(7L)).thenReturn(TOKEN);

        ActivationProperties properties = new ActivationProperties(
                "account-provisioning", "activation-mailer", "http://auth:3000", "secret", LINK_BASE, "noreply@example.com");
        service = new ActivationService(mapper, auth, mail, properties);

        logs = new ListAppender<>();
        logs.start();
        root().addAppender(logs);
    }

    @AfterEach
    void unhook() {
        root().detachAppender(logs);
    }

    private static Logger root() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private static AccountProvisionedEvent event(long clientId) {
        return new AccountProvisionedEvent(UUID.randomUUID().toString(), clientId);
    }

    @Test
    @DisplayName("an event sends one email to the address on file, with a link carrying the token")
    void sendsTheActivationEmail() {
        AccountProvisionedEvent event = event(7);

        service.handle(event);

        assertThat(mail.outbox).hasSize(1);
        ActivationEmail sent = mail.outbox.get(0);
        assertThat(sent.to()).isEqualTo(EMAIL);
        assertThat(sent.link()).isEqualTo(LINK_BASE + "?token=" + TOKEN);
        assertThat(sent.body()).contains(sent.link());
        assertThat(mapper.sent).containsExactly(UUID.fromString(event.eventId()));
    }

    @Test
    @DisplayName("the link carries the token and nothing else identifying")
    void linkCarriesOnlyTheToken() {
        String link = service.link(TOKEN);

        assertThat(link).isEqualTo(LINK_BASE + "?token=" + TOKEN);
        String query = java.net.URI.create(link).getQuery();
        assertThat(query).isEqualTo("token=" + TOKEN);
        assertThat(link).doesNotContain(EMAIL);
    }

    @Test
    @DisplayName("the same eventId delivered twice sends exactly one email")
    void duplicateDeliverySendsOnce() {
        AccountProvisionedEvent event = event(7);

        service.handle(event);
        service.handle(event);

        assertThat(mail.outbox).hasSize(1);
        verify(auth).mintToken(7L);
    }

    @Test
    @DisplayName("a client with no client_profile row is poison: no token minted, nothing sent")
    void noProfileDeadLetters() {
        assertThatThrownBy(() -> service.handle(event(99)))
                .isInstanceOf(NoClientProfileException.class);

        verify(auth, never()).mintToken(anyLong());
        assertThat(mail.outbox).isEmpty();
        assertThat(mapper.sent).isEmpty();
    }

    @Test
    @DisplayName("with auth unreachable the failure escapes for retry, and nothing is recorded as sent")
    void authUnreachableIsRetried() {
        when(auth.mintToken(7L)).thenThrow(new AuthUnavailableException("auth unreachable", null));

        assertThatThrownBy(() -> service.handle(event(7))).isInstanceOf(AuthUnavailableException.class);

        assertThat(mail.outbox).isEmpty();
        assertThat(mapper.sent).isEmpty();
    }

    @Test
    @DisplayName("a mail server failure escapes for retry, and the event is not marked sent")
    void mailFailureIsRetried() {
        mail.failWith = new MailDeliveryException("refused", null);

        assertThatThrownBy(() -> service.handle(event(7))).isInstanceOf(MailDeliveryException.class);

        assertThat(mapper.sent).isEmpty();
    }

    @Test
    @DisplayName("an account that already has a login gets no email, and no error")
    void alreadyClaimedSendsNothing() {
        when(auth.mintToken(7L)).thenThrow(new AccountAlreadyClaimedException(7L));

        service.handle(event(7));

        assertThat(mail.outbox).isEmpty();
    }

    @Test
    @DisplayName("no token and no email address appears in any log line, on success or failure")
    void neverLogsTheTokenOrTheAddress() {
        service.handle(event(7));
        mail.failWith = new MailDeliveryException("refused", null);
        try { service.handle(event(7)); } catch (MailDeliveryException expected) { }
        try { service.handle(event(99)); } catch (NoClientProfileException expected) { }

        assertThat(logs.list).isNotEmpty();
        for (ILoggingEvent line : logs.list) {
            String text = line.getFormattedMessage()
                    + (line.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(line.getThrowableProxy()));
            assertThat(text).doesNotContain(TOKEN).doesNotContain(EMAIL);
        }
        assertThat(mail.outbox.get(0).toString()).doesNotContain(TOKEN).doesNotContain(EMAIL);
    }
}
