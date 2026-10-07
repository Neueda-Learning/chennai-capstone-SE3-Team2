package com.yellow.trade.portfolio;

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
 * The trade-events consumer's wiring: its own group, portfolio-service, shared
 * with nothing (decision log 0013). The offset is committed once a sale is
 * booked. A database blip is retried; a message that can never be read goes
 * to trade-events.DLT at once.
 */
@Configuration
public class PortfolioKafkaConfig {

    static final String DEAD_LETTER_SUFFIX = ".DLT";

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> portfolioListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${portfolio.consumer-group:portfolio-service}") String group) {

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
        // A new group reads what the topic still keeps, 30 days of it: the
        // sales in that window are booked. Booking sends nothing to anyone, a
        // replay books nothing twice, and orders keeps a stray event out.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    static DefaultErrorHandler errorHandler(KafkaTemplate<String, String> deadLetters) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetters,
                (record, ex) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L));
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
