package com.yellow.trade.outbox;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * The relay's own producer. The envelope is already JSON in outbox_event, so
 * it is sent as a string: the order producer's JsonSerializer would wrap it
 * in quotes a second time.
 */
@Configuration
class OutboxKafkaConfig {

    /** How long one send may take, sending included. Bounded so a dead broker cannot hold the scheduler. */
    static final int SEND_TIMEOUT_MS = 15_000;

    @Bean
    KafkaTemplate<String, String> outboxKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // A row is marked published on the broker's acknowledgement, so the
        // acknowledgement has to mean every in-sync replica has it.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        // The defaults wait up to a minute for metadata and two for delivery.
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, SEND_TIMEOUT_MS);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new StringSerializer()));
    }
}
