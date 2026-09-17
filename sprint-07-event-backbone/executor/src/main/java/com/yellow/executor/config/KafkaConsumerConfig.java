package com.yellow.executor.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.executor.events.EventEnvelope;
import com.yellow.executor.events.OrderPlacedPayload;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * The consuming side of {@code orders}.
 *
 * <p>Three settings here are decisions rather than boilerplate.
 *
 * <p>MANUAL ACKNOWLEDGEMENT. Auto-commit is off and the listener acknowledges
 * after the work. Committing first loses an order to a crash between the commit
 * and the work; committing after reprocesses it. Reprocessing is survivable
 * because settlement is guarded, and losing a trade is not -- so the platform
 * takes at-least-once and makes the handler idempotent, which is the whole
 * shape of this sprint.
 *
 * <p>UNKNOWN FIELDS ARE IGNORED. A producer adding an optional field is not a
 * breaking change under the contract. A consumer that threw on one would turn
 * somebody else's additive release into an outage on every account keyed to
 * this consumer's partitions.
 *
 * <p>ERROR-HANDLING DESERIALISER. A message that cannot be deserialised must
 * not kill the container: without this, malformed JSON throws before any
 * listener code runs, the container retries the same bytes for ever, and the
 * partition stops. Wrapping the deserialiser turns that into a failed record
 * the error handler can dead-letter -- which is what story 613 builds on.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${executor.consumer-group:trade-executor}")
    private String consumerGroup;

    private final KafkaProperties kafka;

    public KafkaConsumerConfig(KafkaProperties kafka) {
        this.kafka = kafka;
    }

    @Bean
    public ConsumerFactory<String, EventEnvelope<OrderPlacedPayload>> orderPlacedConsumerFactory(
            ObjectMapper objectMapper) {

        Map<String, Object> props = new HashMap<>(kafka.buildConsumerProperties(null));
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());

        // Fixed by contract. The broker reports which group is reading `orders`
        // and it is asked about at the review.
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);

        // Process, then commit. See the class comment.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // A new group reads the backlog rather than skipping to the end: an
        // order already on the topic when the executor first starts is an order
        // a customer is still waiting for.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Small batches, because each record does database work and an HTTP
        // call. A large poll would sit on records while the broker's session
        // timer runs.
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        JavaType envelopeOfOrderPlaced = objectMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, OrderPlacedPayload.class);

        JsonDeserializer<EventEnvelope<OrderPlacedPayload>> value =
                new JsonDeserializer<>(envelopeOfOrderPlaced, objectMapper, false);
        // The producer does not send type headers, and we would not trust them
        // if it did: a consumer that instantiates whatever class a message names
        // is a deserialisation vulnerability.
        value.setUseTypeHeaders(false);

        // Wrap the JSON deserializer so malformed payloads are surfaced as
        // failed records that the error handler can recover (DLT), instead
        // of bubbling out as poll-level container exceptions.
        ErrorHandlingDeserializer<EventEnvelope<OrderPlacedPayload>> safeValue =
                new ErrorHandlingDeserializer<>(value);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), safeValue);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<OrderPlacedPayload>>
            orderPlacedListenerContainerFactory(
                    ConsumerFactory<String, EventEnvelope<OrderPlacedPayload>> consumerFactory,
                    CommonErrorHandler orderPlacedErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<OrderPlacedPayload>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // The listener decides when the offset moves.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // One consumer per instance. Three partitions means up to three
        // instances share the work; raising concurrency here instead would put
        // several threads in one process against the same partitions, which
        // buys nothing and makes the rebalance demonstration harder to read.
        factory.setConcurrency(1);

        // Story 613: the retry-vs-DLT wiring. Everything the container
        // does with a failed record -- classify, back off, dead-letter --
        // is decided by ConsumerErrorHandling. Keeping it out of this
        // class means the retry policy is one file to read at the review.
        factory.setCommonErrorHandler(orderPlacedErrorHandler);

        return factory;
    }
}
