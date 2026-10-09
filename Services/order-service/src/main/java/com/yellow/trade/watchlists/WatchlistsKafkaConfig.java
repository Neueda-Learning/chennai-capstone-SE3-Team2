package com.yellow.trade.watchlists;

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
 * The market-data consumer's wiring: its own group, watchlist-service, shared
 * with nothing (decision log 0013). It never reads orders or trade-events.
 *
 * The offset is committed once a quote's transaction has committed: its
 * price held, and every alert it crossed fired and queued. A database blip is
 * retried; a message that can never be a quote goes to market-data.DLT at once.
 */
@Configuration
public class WatchlistsKafkaConfig {

    static final String DEAD_LETTER_SUFFIX = ".DLT";
    static final long RETRY_INTERVAL_MS = 1_000L;
    static final long RETRIES = 3L;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> watchlistListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${watchlists.consumer-group:watchlist-service}") String group) {

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
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // A brand-new group starts from now: yesterday's prices fire nothing
        // anyone set an alert for today. After that, the committed offset.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return props;
    }

    static DefaultErrorHandler errorHandler(KafkaTemplate<String, String> deadLetters) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetters,
                (record, ex) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, RETRIES));
        handler.defaultFalse();
        handler.addRetryableExceptions(TransientDataAccessException.class, RetriableException.class);
        handler.setCommitRecovered(true);
        return handler;
    }

    private static KafkaTemplate<String, String> deadLetterTemplate(String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new StringSerializer()));
    }
}
