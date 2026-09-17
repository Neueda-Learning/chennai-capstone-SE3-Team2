package com.yellow.executor.consume;

import com.yellow.executor.settle.LockExhaustedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Story 613 -- retry vs dead-letter.
 *
 * <p>Kafka is at-least-once. The consumer is handed the same record
 * twice, and it is also handed records that are broken. Two failure
 * classes and each is handled the opposite way.
 *
 * <ul>
 *   <li><b>Transient.</b> The broker briefly unreachable, a lost DB
 *       connection, an exhausted optimistic-lock budget. Retry with
 *       exponential backoff -- {@link #MAX_RETRIES} tries after the
 *       initial, 500ms then 2s then 8s (capped at 10s). Roughly 11
 *       seconds of total wait, comfortably inside the 5-minute
 *       {@code max.poll.interval.ms} default so the consumer never
 *       falls out of the group. If the budget is spent, dead-letter.</li>
 *   <li><b>Poison, which is everything else.</b> Malformed JSON, an
 *       unrecognised {@code eventType}, an {@code orderId} that is not
 *       in Postgres, an NPE from a bug that has just landed. No number
 *       of retries fixes any of these -- retrying a poison record blocks
 *       its partition, and a blocked partition stops every account keyed
 *       to it. Dead-letter on the first attempt.</li>
 * </ul>
 *
 * <p>The classifier is defaulted to "not retryable" and only the
 * transient list is added back on. That inversion is the whole point:
 * an unknown bug surfaces as a DLT record and a page, rather than as a
 * partition spinning for 11 seconds against every delivery of a record
 * that will never work.
 *
 * <p>A FAUXNANCE OUTAGE IS NEITHER. {@code OrderExecutionService.execute()}
 * catches {@link com.yellow.executor.quotes.QuoteUnavailableException}
 * and settles the order as {@code REJECTED} with reason {@code NO_PRICE}
 * -- a price feed being down is a business outcome. It never reaches
 * this handler. Dead-lettering it would leave the order at {@code NEW}
 * for ever; retrying it here would spin the partition against a service
 * that is not coming back on that timescale.
 *
 * <p>DEAD-LETTER TOPIC LAYOUT. {@code <topic>.DLT}, one partition, the
 * original bytes as the value (or JSON re-serialised from a rejected
 * object), failure context on the record headers so a teammate
 * replaying from the DLT on Monday morning sees the reason without
 * opening any code.
 */
@Configuration
public class ConsumerErrorHandling {

    private static final Logger log = LoggerFactory.getLogger(ConsumerErrorHandling.class);

    /**
     * Retries after the initial attempt. Four attempts total.
     * Package-private so the tests use the same number the handler
     * does and drift is impossible.
     */
    static final int MAX_RETRIES = 3;

    static final long INITIAL_BACKOFF_MS = 500L;
    static final double BACKOFF_MULTIPLIER = 4.0d;
    static final long MAX_BACKOFF_MS = 10_000L;

    /**
     * The transient class: an exception here is retried. Anything not
     * assignable to one of these is classified as poison and dead-
     * lettered on the first attempt.
     *
     * <p>Parents where they exist: {@link TransientDataAccessException}
     * covers {@code DataAccessResourceFailureException}, {@code
     * TransientDataAccessResourceException} and {@code
     * RecoverableDataAccessException}; {@link RetriableException} is
     * Kafka's own marker for a broker blip that will pass. Both spare
     * this list from having to enumerate every subclass some future
     * release might add.
     */
    static final List<Class<? extends Exception>> TRANSIENT_EXCEPTIONS = List.of(
            TransientDataAccessException.class,
            RetriableException.class,
            LockExhaustedException.class);

    /**
     * The poison exceptions we recognise BY NAME. Everything not on the
     * transient list is treated as poison anyway (see
     * {@link org.springframework.kafka.listener.DefaultErrorHandler#defaultFalse}
     * below), so this list does not decide 'is it poison'. It decides
     * which class the {@code x-failure-class-fqcn} header names when a
     * wrapper hides the interesting cause -- {@code
     * DeserializationException} wrapping a raw
     * {@code RuntimeException} would otherwise dead-letter with FQCN
     * naming the wrapper's cause instead of the deserialisation.
     */
    static final List<Class<? extends Exception>> KNOWN_POISON = List.of(
            DeserializationException.class,
            UnexpectedEventTypeException.class,
            UnknownOrderException.class);

    /** Header names -- agreed in todo.md §5 so 615's Python side can read them. */
    static final String H_FAILURE_REASON     = "x-failure-reason";
    static final String H_FAILURE_CLASS      = "x-failure-class";
    static final String H_FAILURE_CLASS_FQCN = "x-failure-class-fqcn";
    static final String H_ORIGINAL_TOPIC     = "x-original-topic";
    static final String H_ORIGINAL_PARTITION = "x-original-partition";
    static final String H_ORIGINAL_OFFSET    = "x-original-offset";
    static final String H_ATTEMPT_COUNT      = "x-attempt-count";
    static final String H_FAILED_AT          = "x-failed-at";

    static final String CLASS_POISON    = "POISON";
    static final String CLASS_TRANSIENT = "TRANSIENT";

    /**
     * DLT records carry either the original bytes (a deserialisation
     * failure preserved the raw payload) or JSON re-serialised from a
     * rejected object. {@link ByteArraySerializer} plus the recoverer's
     * own logic handles both without a per-payload template.
     */
    @Bean
    public ProducerFactory<String, byte[]> dltProducerFactory(KafkaProperties kafka) {
        Map<String, Object> props = new HashMap<>(kafka.buildProducerProperties(null));
        // Same producer contract as everywhere else on the platform
        // (todo.md §1.7): a DLT publish that silently drops leaves
        // the poison record processed nowhere.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new DefaultKafkaProducerFactory<>(props,
            new StringSerializer(), new ByteArraySerializer());
    }

    @Bean
        public KafkaTemplate<String, byte[]> dltKafkaTemplate(
            ProducerFactory<String, byte[]> dltProducerFactory) {
        return new KafkaTemplate<>(dltProducerFactory);
    }

    /**
     * The error handler for the {@code orders} listener.
     *
     * <p>The classifier is defaulted to {@code false} (not retryable),
     * so every exception dead-letters on the first attempt UNLESS it
     * (or one of its causes) is assignable to something on
     * {@link #TRANSIENT_EXCEPTIONS}.
     */
    @Bean
    public DefaultErrorHandler orderPlacedErrorHandler(
            KafkaOperations<String, byte[]> dltKafkaTemplate, Clock clock) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                // Route <topic> -> <topic>.DLT. Partition -1 lets the
                // broker choose; the DLT topics are one-partition each
                // (no ordering needed for investigation), so it does
                // not matter which.
                (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", -1));

        recoverer.setHeadersFunction((rec, ex) -> buildHeaders(rec, ex, clock));

        ExponentialBackOffWithMaxRetries backoff =
                new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backoff.setInitialInterval(INITIAL_BACKOFF_MS);
        backoff.setMultiplier(BACKOFF_MULTIPLIER);
        backoff.setMaxInterval(MAX_BACKOFF_MS);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backoff);

        // Default: not retryable. Anything on the transient list below
        // is added back on. This is the "everything else is poison"
        // decision, made explicit -- an unknown exception surfaces fast
        // rather than looping in the retry harness.
        handler.defaultFalse();
        for (Class<? extends Exception> transientType : TRANSIENT_EXCEPTIONS) {
            handler.addRetryableExceptions(transientType);
        }

        // Commit the offset for a record that was routed to the DLT --
        // otherwise a container restart re-delivers the poison and it
        // dead-letters again on every restart.
        handler.setCommitRecovered(true);

        // Log every retry so 'was this record retried or dead-lettered
        // straight away' is answerable from the log alone.
        handler.setRetryListeners((rec, ex, deliveryAttempt) ->
                log.warn("retry {} for {}-{}@{}: {}",
                        deliveryAttempt, rec.topic(), rec.partition(), rec.offset(),
                        rootCause(ex).toString()));

        return handler;
    }

    /**
     * The header set the DLT recoverer stamps on every dead-lettered
     * record. Package-private and static so the unit test drives the
     * same code the production handler does, not a copy.
     *
     * <p>The classification, the {@code x-failure-class-fqcn} and the
     * {@code x-failure-reason} all come from a single walk of the
     * cause chain, so a record ever labelled TRANSIENT names a
     * transient class in its FQCN and cannot be labelled POISON in
     * one header and TRANSIENT in another.
     */
    static Headers buildHeaders(ConsumerRecord<?, ?> rec, Exception ex, Clock clock) {
        Analysis analysis = analyse(ex);
        String failureClass = analysis.transientCause ? CLASS_TRANSIENT : CLASS_POISON;
        // Transient dead-letters after the retry budget is spent (one
        // initial plus MAX_RETRIES); poison dead-letters on the first
        // attempt.
        int attempts = analysis.transientCause ? (MAX_RETRIES + 1) : 1;

        Headers headers = new RecordHeaders();
        headers.add(H_FAILURE_REASON,     bytes(safeMessage(analysis.cause)));
        headers.add(H_FAILURE_CLASS,      bytes(failureClass));
        headers.add(H_FAILURE_CLASS_FQCN, bytes(analysis.cause.getClass().getName()));
        headers.add(H_ORIGINAL_TOPIC,     bytes(rec.topic()));
        headers.add(H_ORIGINAL_PARTITION, bytes(Integer.toString(rec.partition())));
        headers.add(H_ORIGINAL_OFFSET,    bytes(Long.toString(rec.offset())));
        headers.add(H_ATTEMPT_COUNT,      bytes(Integer.toString(attempts)));
        headers.add(H_FAILED_AT,          bytes(Instant.now(clock).toString()));
        return headers;
    }

    /**
     * One walk of the cause chain that identifies the most informative
     * exception and answers 'is this transient?'. Spring wraps a
     * listener exception in {@code ListenerExecutionFailedException}
     * whose cause is what the listener threw, so a naive
     * {@code ex.getClass()} lies to a teammate reading the DLT.
     *
     * <p>Priority for the {@code cause}:
     * <ol>
     *   <li>the first exception in the chain assignable to a transient
     *       class (and we return {@code transientCause=true})</li>
     *   <li>the first exception in the chain assignable to a poison
     *       class (and we return {@code transientCause=false})</li>
     *   <li>otherwise the root cause, treated as poison -- the
     *       'everything else is poison' default of the classifier</li>
     * </ol>
     */
    static Analysis analyse(Throwable ex) {
        Throwable current = ex;
        for (int i = 0; i < 20 && current != null; i++) {
            for (Class<? extends Exception> type : TRANSIENT_EXCEPTIONS) {
                if (type.isInstance(current)) {
                    return new Analysis(current, true);
                }
            }
            for (Class<? extends Exception> type : KNOWN_POISON) {
                if (type.isInstance(current)) {
                    return new Analysis(current, false);
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return new Analysis(rootCause(ex), false);
    }

    /** Convenience for tests that only care about the transient/poison bit. */
    static boolean isTransient(Throwable cause) {
        return analyse(cause).transientCause;
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        // Bounded walk to guard against a defensively broken exception
        // whose cause chain loops back on itself.
        for (int i = 0; i < 20 && current.getCause() != null && current.getCause() != current; i++) {
            current = current.getCause();
        }
        return current;
    }

    /** Package-private carrier: the classified exception, and its verdict. */
    record Analysis(Throwable cause, boolean transientCause) { }

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        return (message == null || message.isBlank()) ? t.getClass().getSimpleName() : message;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
