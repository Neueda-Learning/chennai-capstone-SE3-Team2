package com.yellow.executor.consume;

import com.yellow.executor.settle.LockExhaustedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.serializer.DeserializationException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * The header enricher, in isolation. What the review reads is what the
 * recoverer stamps on a DLT record, and that is what
 * ConsumerErrorHandling#buildHeaders decides.
 */
class ConsumerErrorHandlingClassifierTest {

    private static final Instant NOW = Instant.parse("2026-09-17T09:14:24Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    // ---------------------------------------------------------------- poison

    @Test
    @DisplayName("malformed JSON is POISON and dead-letters on attempt one")
    void deserializationExceptionIsPoison() {
        DeserializationException ex = new DeserializationException(
                "unrecognised token", new byte[]{'{'}, false, new RuntimeException("bad"));

        Headers h = ConsumerErrorHandling.buildHeaders(record(), ex, clock);

        assertHeader(h, ConsumerErrorHandling.H_FAILURE_CLASS,      "POISON");
        assertHeader(h, ConsumerErrorHandling.H_ATTEMPT_COUNT,      "1");
        assertHeader(h, ConsumerErrorHandling.H_FAILURE_CLASS_FQCN,
                DeserializationException.class.getName());
    }

    @Test
    @DisplayName("an unrecognised eventType is POISON and dead-letters on attempt one")
    void unexpectedEventTypeIsPoison() {
        UnexpectedEventTypeException ex = new UnexpectedEventTypeException("SOMETHING_ELSE");

        Headers h = ConsumerErrorHandling.buildHeaders(record(), ex, clock);

        assertHeader(h, ConsumerErrorHandling.H_FAILURE_CLASS,      "POISON");
        assertHeader(h, ConsumerErrorHandling.H_ATTEMPT_COUNT,      "1");
    }

    @Test
    @DisplayName("an orderId that is not in Postgres is POISON, not transient")
    void unknownOrderIsPoison() {
        UnknownOrderException ex = new UnknownOrderException(UUID.randomUUID());

        Headers h = ConsumerErrorHandling.buildHeaders(record(), ex, clock);

        assertHeader(h, ConsumerErrorHandling.H_FAILURE_CLASS, "POISON");
        assertHeader(h, ConsumerErrorHandling.H_ATTEMPT_COUNT, "1");
    }

    @Test
    @DisplayName("a novel bug (NullPointerException) is POISON by default -- surface fast")
    void anUnknownExceptionIsPoisonByDefault() {
        // The design decision: everything not on the transient list is
        // poison.
        NullPointerException ex = new NullPointerException("payload was null");

        Headers h = ConsumerErrorHandling.buildHeaders(record(), ex, clock);

        assertHeader(h, ConsumerErrorHandling.H_FAILURE_CLASS, "POISON");
        assertHeader(h, ConsumerErrorHandling.H_ATTEMPT_COUNT, "1");
    }

    // ------------------------------------------------------------- transient

    @Test
    @DisplayName("a TransientDataAccessResourceException is TRANSIENT (matched via its Spring parent)")
    void transientDataAccessResourceIsTransient() {
        // The typed exception Spring's SQLExceptionSubclassTranslator raises
        // for a transient connection failure -- SQLSTATE 08*.
        TransientDataAccessResourceException ex =
                new TransientDataAccessResourceException("connection reset");

        Headers h = ConsumerErrorHandling.buildHeaders(record(), ex, clock);

        assertHeader(h, ConsumerErrorHandling.H_FAILURE_CLASS, "TRANSIENT");
        assertHeader(h, ConsumerErrorHandling.H_ATTEMPT_COUNT,
                Integer.toString(ConsumerErrorHandling.MAX_RETRIES + 1));
    }

    @Test
    @DisplayName("a Kafka RetriableException is TRANSIENT")
    void kafkaRetriableIsTransient() {
        // NotEnoughReplicasException is a concrete Kafka RetriableException
        // -- broker had insufficient replicas at the moment it saw us.
        org.apache.kafka.common.errors.NotEnoughReplicasException ex =
                new org.apache.kafka.common.errors.NotEnoughReplicasException("min.insync.replicas=2, got 1");

        Headers h = ConsumerErrorHandling.buildHeaders(record(), ex, clock);

        assertHeader(h, ConsumerErrorHandling.H_FAILURE_CLASS, "TRANSIENT");
        assertHeader(h, ConsumerErrorHandling.H_ATTEMPT_COUNT,
                Integer.toString(ConsumerErrorHandling.MAX_RETRIES + 1));
    }

    @Test
    @DisplayName("LockExhaustedException is TRANSIENT -- retry the whole record, not just the CAS")
    void lockExhaustedIsTransient() {
        // The optimistic-lock budget inside FullSettlement is the CAS retry.
        LockExhaustedException ex = new LockExhaustedException("account 3, 5 attempts");

        Headers h = ConsumerErrorHandling.buildHeaders(record(), ex, clock);

        assertHeader(h, ConsumerErrorHandling.H_FAILURE_CLASS, "TRANSIENT");
        assertHeader(h, ConsumerErrorHandling.H_ATTEMPT_COUNT,
                Integer.toString(ConsumerErrorHandling.MAX_RETRIES + 1));
    }

    // -------------------------------------------------------- cause chain

    @Test
    @DisplayName("classification walks the cause chain -- Spring wraps listener exceptions")
    void causeChainIsWalked() {
        // Real production shape: Spring wraps whatever the listener threw in
        // a ListenerExecutionFailedException, whose cause is the actual
        UnknownOrderException real = new UnknownOrderException(UUID.randomUUID());
        Exception wrapped = new ListenerExecutionFailedException("listener failed", real);

        Headers h = ConsumerErrorHandling.buildHeaders(record(), wrapped, clock);

        // Answer must be identical to the un-wrapped case.
        assertHeader(h, ConsumerErrorHandling.H_FAILURE_CLASS, "POISON");
        assertHeader(h, ConsumerErrorHandling.H_ATTEMPT_COUNT, "1");
        // The FQCN is the ROOT cause, not the wrapper -- so a teammate
        // grepping the DLT for "UnknownOrderException" finds the record.
        assertHeader(h, ConsumerErrorHandling.H_FAILURE_CLASS_FQCN,
                UnknownOrderException.class.getName());
    }

    // ---------------------------------------------------- context headers

    @Test
    @DisplayName("origin headers name the topic, partition and offset the record came from")
    void originHeadersAreEmitted() {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>(
                "orders", 2, 148L, "acc-3", "{}");

        Headers h = ConsumerErrorHandling.buildHeaders(
                rec, new NullPointerException("boom"), clock);

        assertHeader(h, ConsumerErrorHandling.H_ORIGINAL_TOPIC,     "orders");
        assertHeader(h, ConsumerErrorHandling.H_ORIGINAL_PARTITION, "2");
        assertHeader(h, ConsumerErrorHandling.H_ORIGINAL_OFFSET,    "148");
    }

    @Test
    @DisplayName("x-failed-at is RFC 3339 UTC, taken from the injected clock")
    void failedAtIsFixedClockAware() {
        Headers h = ConsumerErrorHandling.buildHeaders(
                record(), new NullPointerException("boom"), clock);

        Header failedAt = h.lastHeader(ConsumerErrorHandling.H_FAILED_AT);
        assertThat(failedAt, is(notNullValue()));
        String iso = new String(failedAt.value(), StandardCharsets.UTF_8);
        assertThat(iso, is(equalTo(NOW.toString())));
        // Sanity: the string is RFC 3339 UTC form -- last character is Z.
        assertThat(iso, startsWith("2026-09-17T"));
    }

    @Test
    @DisplayName("x-failure-reason falls back to the class name when the exception carries no message")
    void reasonFallsBackToClassName() {
        Headers h = ConsumerErrorHandling.buildHeaders(
                record(), new NullPointerException(), clock);

        assertHeader(h, ConsumerErrorHandling.H_FAILURE_REASON,
                NullPointerException.class.getSimpleName());
    }

    // -------------------------------------------------------------- helpers

    private static ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>("orders", 0, 0L, "acc-3", "{}");
    }

    private static void assertHeader(Headers headers, String name, String expected) {
        Header header = headers.lastHeader(name);
        assertThat("missing header: " + name, header, is(notNullValue()));
        String actual = new String(header.value(), StandardCharsets.UTF_8);
        assertThat("header " + name, actual, is(equalTo(expected)));
    }
}
