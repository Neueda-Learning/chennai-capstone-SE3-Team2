package com.yellow.trade.activation;

import com.yellow.trade.activation.ActivationExceptions.AuthUnavailableException;
import com.yellow.trade.activation.ActivationExceptions.MailDeliveryException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The activation mailer's consumer: its own group, its own container factory
 * and its own dead-letter wiring, following the executor's ConsumerErrorHandling.
 *
 * Values are read as plain strings and parsed in the listener. A message that
 * fails therefore reaches account-provisioning.DLT as the exact text it arrived
 * as, whether it failed to parse or failed further on.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(ActivationProperties.class)
public class ActivationKafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(ActivationKafkaConfig.class);

    /** Retries after the first attempt: four attempts, about 11 seconds in all. */
    static final int MAX_RETRIES = 3;
    static final long INITIAL_BACKOFF_MS = 500L;
    static final double BACKOFF_MULTIPLIER = 4.0d;
    static final long MAX_BACKOFF_MS = 10_000L;

    /**
     * Retried with backoff, then dead-lettered: auth restarting, the mail
     * server refusing for a moment, a dropped database connection. Anything
     * else is poison and dead-letters on the first attempt.
     */
    static final List<Class<? extends Exception>> TRANSIENT_EXCEPTIONS = List.of(
            AuthUnavailableException.class,
            MailDeliveryException.class,
            TransientDataAccessException.class,
            RetriableException.class);

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> activationListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            ActivationProperties properties) {

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, properties.consumerGroup());
        // Process, then commit: a crash re-delivers rather than loses.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // A new group reads the backlog: a customer provisioned while this was
        // down is still waiting for their email.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Each record is an HTTP call and an SMTP send; keep polls small.
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer()));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(1);
        factory.setCommonErrorHandler(activationErrorHandler(activationDltTemplate(bootstrapServers)));
        return factory;
    }

    KafkaTemplate<String, String> activationDltTemplate(String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // A dead-letter write that silently drops leaves the record nowhere.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new StringSerializer()));
    }

    static DefaultErrorHandler activationErrorHandler(KafkaTemplate<String, String> dlt) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dlt, (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", -1));
        recoverer.setHeadersFunction(ActivationKafkaConfig::buildHeaders);

        ExponentialBackOffWithMaxRetries backoff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backoff.setInitialInterval(INITIAL_BACKOFF_MS);
        backoff.setMultiplier(BACKOFF_MULTIPLIER);
        backoff.setMaxInterval(MAX_BACKOFF_MS);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backoff);
        handler.defaultFalse();
        TRANSIENT_EXCEPTIONS.forEach(handler::addRetryableExceptions);
        handler.setCommitRecovered(true);
        handler.setRetryListeners((rec, ex, attempt) ->
                log.warn("activation retry {} for {}-{}@{}: {}", attempt, rec.topic(), rec.partition(), rec.offset(),
                        rootCause(ex).getClass().getSimpleName()));
        return handler;
    }

    /** Same header names as the executor's dead-letter records, so one reader handles both. */
    static Headers buildHeaders(ConsumerRecord<?, ?> rec, Exception ex) {
        Throwable cause = classifiedCause(ex);
        boolean transientCause = isTransient(ex);
        Headers headers = new RecordHeaders();
        headers.add("x-failure-reason", bytes(safeMessage(cause)));
        headers.add("x-failure-class", bytes(transientCause ? "TRANSIENT" : "POISON"));
        headers.add("x-failure-class-fqcn", bytes(cause.getClass().getName()));
        headers.add("x-original-topic", bytes(rec.topic()));
        headers.add("x-original-partition", bytes(Integer.toString(rec.partition())));
        headers.add("x-original-offset", bytes(Long.toString(rec.offset())));
        headers.add("x-attempt-count", bytes(Integer.toString(transientCause ? MAX_RETRIES + 1 : 1)));
        headers.add("x-failed-at", bytes(Instant.now().toString()));
        return headers;
    }

    static boolean isTransient(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            for (Class<? extends Exception> type : TRANSIENT_EXCEPTIONS) {
                if (type.isInstance(t)) return true;
            }
        }
        return false;
    }

    /** The first exception of ours in the chain, past Spring's listener wrapper. */
    private static Throwable classifiedCause(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getClass().getName().startsWith(ActivationExceptions.class.getName())) return t;
        }
        return rootCause(ex);
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        for (int i = 0; i < 20 && current.getCause() != null && current.getCause() != current; i++) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Only our own exception messages are copied into the header: they carry
     * ids and never an address or a token. A foreign message (an SMTP error can
     * quote the recipient) is replaced by its class name.
     */
    private static String safeMessage(Throwable t) {
        boolean ours = t.getClass().getName().startsWith(ActivationExceptions.class.getName());
        String message = t.getMessage();
        return ours && message != null && !message.isBlank() ? message : t.getClass().getSimpleName();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
