package com.yellow.trade.notifications;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * The trade-events consumer's wiring: its own group, notification-service,
 * shared with nothing (decision log 0013), and its own container factory.
 *
 * The offset is committed once the listener returns, which is once the
 * notification is recorded QUEUED (decision log 0006): never before, so a
 * crash re-delivers rather than loses, and never after a send, so a slow mail
 * server never stalls a partition. A database blip is retried; a message that
 * can never be a notification goes to trade-events.DLT on its first attempt.
 */
@Configuration
public class NotificationsKafkaConfig {

    static final String DEAD_LETTER_SUFFIX = ".DLT";
    static final long RETRY_INTERVAL_MS = 1_000L;
    static final long RETRIES = 3L;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> notificationListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${notifications.consumer-group:notification-service}") String group) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerProperties(bootstrapServers, group),
                new StringDeserializer(), new StringDeserializer()));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(1);
        factory.setCommonErrorHandler(errorHandler(deadLetterTemplate(bootstrapServers)));
        return factory;
    }

    static Map<String, Object> consumerProperties(String bootstrapServers, String group) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        // Recorded, then committed: the container commits after the listener returns.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // A brand-new group starts from now. trade-events keeps 30 days, and
        // switching notifications on must not mail every customer a month of
        // old trades. After that the committed offset carries it across a
        // restart, so a fill while this was down is still owed its message.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return props;
    }

    static DefaultErrorHandler errorHandler(KafkaTemplate<String, String> deadLetters) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetters,
                (record, ex) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, RETRIES));
        // Only what may pass is retried; the rest is poison and dead-letters at once.
        handler.defaultFalse();
        handler.addRetryableExceptions(TransientDataAccessException.class, RetriableException.class);
        handler.setCommitRecovered(true);
        return handler;
    }

    private static KafkaTemplate<String, String> deadLetterTemplate(String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // A dead-letter write that silently drops leaves the record nowhere.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new StringSerializer()));
    }
}
