package com.yellow.executor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.executor.events.EventEnvelope;
import com.yellow.executor.events.QuotePayload;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/** The producing side of market-data. Story 614. */
@Configuration
public class MarketDataProducerConfig {

    @Bean
    public ProducerFactory<String, EventEnvelope<QuotePayload>> marketDataProducerFactory(
            KafkaProperties kafka, ObjectMapper objectMapper) {

        Map<String, Object> props = new HashMap<>(kafka.buildProducerProperties(null));
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        JsonSerializer<EventEnvelope<QuotePayload>> valueSerializer =
                new JsonSerializer<>(objectMapper);
        // Downstream consumers must not trust type headers: a consumer that
        // instantiates whatever class a message names is a deserialisation
        valueSerializer.setAddTypeInfo(false);

        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, EventEnvelope<QuotePayload>> marketDataTemplate(
            ProducerFactory<String, EventEnvelope<QuotePayload>> marketDataProducerFactory) {
        return new KafkaTemplate<>(marketDataProducerFactory);
    }
}
