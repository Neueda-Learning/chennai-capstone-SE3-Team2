package com.yellow.trade.activation;

import com.yellow.trade.activation.ActivationExceptions.AuthRefusedException;
import com.yellow.trade.activation.ActivationExceptions.AuthUnavailableException;
import com.yellow.trade.activation.ActivationExceptions.MailDeliveryException;
import com.yellow.trade.activation.ActivationExceptions.MalformedEventException;
import com.yellow.trade.activation.ActivationExceptions.NoClientProfileException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.mail.MailSendException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ActivationKafkaConfigTest {

    private static String header(Headers headers, String name) {
        return new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>("account-provisioning", 1, 42L, "7", "{}");
    }

    @Test
    @DisplayName("auth down and mail refused are retried; everything else dead-letters at once")
    void classification() {
        assertThat(ActivationKafkaConfig.isTransient(new AuthUnavailableException("down", null))).isTrue();
        assertThat(ActivationKafkaConfig.isTransient(new MailDeliveryException("refused", null))).isTrue();
        assertThat(ActivationKafkaConfig.isTransient(
                new ListenerExecutionFailedException("wrapped", new AuthUnavailableException("down", null)))).isTrue();

        assertThat(ActivationKafkaConfig.isTransient(new NoClientProfileException(7))).isFalse();
        assertThat(ActivationKafkaConfig.isTransient(new MalformedEventException("not JSON"))).isFalse();
        assertThat(ActivationKafkaConfig.isTransient(new AuthRefusedException("bad secret"))).isFalse();
        assertThat(ActivationKafkaConfig.isTransient(new NullPointerException())).isFalse();
    }

    @Test
    @DisplayName("a dead-letter record names the real cause, past Spring's wrapper, and where it came from")
    void headersForPoison() {
        Headers headers = ActivationKafkaConfig.buildHeaders(record(),
                new ListenerExecutionFailedException("wrapped", new NoClientProfileException(7)));

        assertThat(header(headers, "x-failure-class")).isEqualTo("POISON");
        assertThat(header(headers, "x-failure-reason")).isEqualTo("no client_profile row for client 7");
        assertThat(header(headers, "x-failure-class-fqcn")).endsWith("NoClientProfileException");
        assertThat(header(headers, "x-original-topic")).isEqualTo("account-provisioning");
        assertThat(header(headers, "x-original-partition")).isEqualTo("1");
        assertThat(header(headers, "x-original-offset")).isEqualTo("42");
        assertThat(header(headers, "x-attempt-count")).isEqualTo("1");
    }

    @Test
    @DisplayName("a transient failure dead-letters after the full retry budget")
    void headersForTransient() {
        Headers headers = ActivationKafkaConfig.buildHeaders(record(), new AuthUnavailableException("auth unreachable", null));

        assertThat(header(headers, "x-failure-class")).isEqualTo("TRANSIENT");
        assertThat(header(headers, "x-attempt-count")).isEqualTo(String.valueOf(ActivationKafkaConfig.MAX_RETRIES + 1));
    }

    @Test
    @DisplayName("a mail server's own message, which can quote the recipient, never reaches the header")
    void foreignMessagesAreNotCopied() {
        MailSendException smtp = new MailSendException("550 mailbox unavailable: priya.menon@example.com");
        Headers headers = ActivationKafkaConfig.buildHeaders(record(), smtp);

        assertThat(header(headers, "x-failure-reason")).isEqualTo("MailSendException").doesNotContain("@");
    }
}
