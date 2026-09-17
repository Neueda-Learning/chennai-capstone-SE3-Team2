package com.yellow.executor.consume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.executor.config.KafkaConsumerConfig;
import com.yellow.executor.events.EventEnvelope;
import com.yellow.executor.events.OrderPlacedPayload;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three assessed paths for story 613, against an embedded broker.
 *
 * <ol>
 *   <li>A MALFORMED MESSAGE lands on {@code orders.DLT} on the first
 *       attempt. Not retried, and the DLT record carries the failure
 *       context in {@code x-*} headers.</li>
 *   <li>A TRANSIENT FAILURE is retried, and if the underlying condition
 *       clears within the retry budget, the record is processed and no
 *       DLT record is produced.</li>
 *   <li>A POISON MESSAGE DOES NOT BLOCK THE PARTITION. A good record
 *       queued behind the poison one on the same partition -- same key
 *       so same partition -- is still processed. That is the property
 *       the review asks for: one bad message must not stop every
 *       account keyed to a partition.</li>
 * </ol>
 *
 * <p>Wiring uses the production {@link ConsumerErrorHandling} bean and
 * a container built the same way as {@link KafkaConsumerConfig}, with
 * the one difference that the listener is a stub. The exception the
 * stub throws is what varies per test.
 */
@SpringJUnitConfig(ConsumerErrorHandlingIntegrationTest.TestConfig.class)
@EmbeddedKafka(
        topics = {"orders", "orders.DLT"},
        partitions = 1,
        controlledShutdown = true,
        brokerProperties = {"auto.create.topics.enable=false"})
// Recreate the broker and container between tests. Sharing them
// otherwise means one test's DLT records show up in another test's
// observer, and the "no DLT record expected" assertion becomes flaky.
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class ConsumerErrorHandlingIntegrationTest {

    private static final String ORDERS_TOPIC = "orders";
    private static final String ORDERS_DLT   = "orders.DLT";
    private static final String KEY          = "acc-3";

    /**
     * Well-formed JSON payload matching the ORDER_PLACED envelope. The
     * exact orderId does not matter -- the stub listener decides what
     * happens next, so this test never hits Postgres.
     */
    private static final String WELL_FORMED_ENVELOPE = """
            {
              "eventId":"%s",
              "eventType":"ORDER_PLACED",
              "eventTime":"2026-09-17T09:14:22Z",
              "source":"trade-api",
              "schemaVersion":1,
              "payload":{
                "orderId":"%s",
                "accountId":3,
                "symbol":"ITC.NS",
                "side":"BUY",
                "quantity":10,
                "price":500.00,
                "idempotencyKey":"test-key",
                "createdOn":"2026-09-17T09:14:22Z"
              }
            }
            """;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private StubExecution stub;

    @Autowired
    private KafkaTemplate<String, String> ordersProducer;

    @Autowired
    private ConcurrentMessageListenerContainer<String, EventEnvelope<OrderPlacedPayload>> testContainer;

    private Consumer<String, byte[]> dltObserver;

    @BeforeEach
    void wireDltObserver() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "dlt-observer-" + UUID.randomUUID(), "true", broker);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        dltObserver = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        dltObserver.subscribe(List.of(ORDERS_DLT));

        // Join the DLT consumer group before test records are produced.
        dltObserver.poll(Duration.ofMillis(100));

        // Ensure the main listener has an assigned partition before sending
        // test records, otherwise assertions race the container startup.
        ContainerTestUtils.waitForAssignment(testContainer, 1);
    }

    @AfterEach
    void closeObserver() {
        stub.reset();
        if (dltObserver != null) {
            dltObserver.close();
        }
    }

    // ------------------------------------------------------------- path 1

    @Test
    @DisplayName("malformed JSON dead-letters on the first attempt (no retries)")
    void malformedIsDeadLetteredImmediately() {
        // Send garbage that the JsonDeserializer cannot parse. The
        // ErrorHandlingDeserializer catches the exception, so the
        // container does not die; the DefaultErrorHandler then routes
        // it via the recoverer to the DLT.
        ordersProducer.send(new ProducerRecord<>(ORDERS_TOPIC, 0, KEY,
                "this is not JSON at all { \"broken"));
        ordersProducer.flush();

        ConsumerRecord<String, byte[]> dlt = pollOneDlt();
        assertThat("expected a DLT record", dlt, is(notNullValue()));

        // Attempt count must be exactly 1 -- retrying a message we
        // cannot parse blocks the partition and helps nobody.
        assertHeader(dlt, ConsumerErrorHandling.H_ATTEMPT_COUNT, "1");
        assertHeader(dlt, ConsumerErrorHandling.H_FAILURE_CLASS,  "POISON");
        assertHeader(dlt, ConsumerErrorHandling.H_ORIGINAL_TOPIC, "orders");

        // And the listener was never called: the record never got past
        // the deserialiser.
        assertThat(stub.callCount(), is(0));
    }

    // ------------------------------------------------------------- path 2

    @Test
    @DisplayName("a transient failure is retried and then succeeds -- no DLT record")
    void transientIsRetriedAndSucceeds() {
        // Fail once with a Spring-transient exception, then succeed.
        // The DefaultErrorHandler classifier walks the cause chain and
        // matches TransientDataAccessException, so the record is
        // retried on backoff rather than dead-lettered.
        stub.failFirst(new TransientDataAccessResourceException(
                "connection to Postgres was reset"));

        UUID orderId = UUID.randomUUID();
        ordersProducer.send(new ProducerRecord<>(ORDERS_TOPIC, 0, KEY,
                WELL_FORMED_ENVELOPE.formatted(UUID.randomUUID(), orderId)));
        ordersProducer.flush();

        assertTrue(awaitSuccess(stub, 15, TimeUnit.SECONDS),
                "listener should eventually succeed after the transient failure clears");

        // No DLT record within a reasonable window.
        ConsumerRecord<String, byte[]> dlt = pollOneDlt(Duration.ofSeconds(3));
        assertThat("no DLT record expected for a transient failure that later succeeded",
                dlt, is(org.hamcrest.Matchers.nullValue()));

        // Listener saw the record at least twice -- one failure and one
        // successful retry. It should not have been called more than the
        // retry budget: an unbounded retry loop is exactly what the
        // handler prevents.
        assertThat(stub.callCount(), is(greaterThan(1)));
        assertThat(stub.callCount(),
                is(org.hamcrest.Matchers.lessThanOrEqualTo(ConsumerErrorHandling.MAX_RETRIES + 1)));
    }

    // ------------------------------------------------------------- path 3

    @Test
    @DisplayName("a poison record does not block the partition -- a good record behind it is still processed")
    void poisonDoesNotBlockPartition() {
        // Two records on the same key, so both on the same partition.
        // The first is malformed JSON: poison. The second is a valid
        // ORDER_PLACED. If the poison one blocks the partition, the
        // second never lands -- and that is exactly the failure mode
        // this test exists to guard against.
        ordersProducer.send(new ProducerRecord<>(ORDERS_TOPIC, 0, KEY,
                "poison { garbage"));
        UUID goodOrder = UUID.randomUUID();
        ordersProducer.send(new ProducerRecord<>(ORDERS_TOPIC, 0, KEY,
                WELL_FORMED_ENVELOPE.formatted(UUID.randomUUID(), goodOrder)));
        ordersProducer.flush();

        // The listener should be called for the good record.
        assertTrue(awaitSuccess(stub, 15, TimeUnit.SECONDS),
                "the good record must still be processed after the poison one is DLT'd");

        // And the poison one lands on the DLT.
        ConsumerRecord<String, byte[]> dlt = pollOneDlt();
        assertThat("poison record should be on the DLT", dlt, is(notNullValue()));
        assertHeader(dlt, ConsumerErrorHandling.H_FAILURE_CLASS, "POISON");
        assertHeader(dlt, ConsumerErrorHandling.H_ATTEMPT_COUNT, "1");
    }

    // ----------------------------------------------------- test utilities

    private static boolean awaitSuccess(StubExecution stub, long timeout, TimeUnit unit) {
        try {
            return stub.awaitSuccess(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the listener to succeed", e);
        }
    }

    private ConsumerRecord<String, byte[]> pollOneDlt() {
        return pollOneDlt(Duration.ofSeconds(10));
    }

    private ConsumerRecord<String, byte[]> pollOneDlt(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            org.apache.kafka.clients.consumer.ConsumerRecords<String, byte[]> records =
                    dltObserver.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, byte[]> r : records) {
                return r;
            }
        }
        return null;
    }

    private static void assertHeader(ConsumerRecord<?, ?> record, String name, String expected) {
        Header header = record.headers().lastHeader(name);
        assertThat("missing header: " + name, header, is(notNullValue()));
        String actual = new String(header.value(), StandardCharsets.UTF_8);
        assertThat("header " + name, actual, is(equalTo(expected)));
    }

    // --------------------------------------------- the test Spring context

    /**
     * The minimum wiring to bring up the production error handler and a
     * container that uses it. The listener itself is {@link StubExecution}
     * so a test can decide, per case, whether the listener throws.
     */
    @Configuration
    @Import(ConsumerErrorHandling.class)
    static class TestConfig {

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        public Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        public StubExecution stubExecution() {
            return new StubExecution();
        }

        @Bean
        public KafkaProperties kafkaProperties(EmbeddedKafkaBroker broker) {
            KafkaProperties props = new KafkaProperties();
            props.setBootstrapServers(List.of(broker.getBrokersAsString().split(",")));
            return props;
        }

        // The production consumer factory / container factory / error
        // handler wiring, imported from the same class the executor
        // uses in main(). Bringing it in as a @Configuration would drag
        // its @EnableKafka along; we do not want annotation-driven
        // listeners here, only programmatic ones.
        @Bean
        public KafkaConsumerConfig kafkaConsumerConfig(KafkaProperties props) {
            return new KafkaConsumerConfig(props);
        }

        // A producer for TEST-side sending. Value is a raw String so
        // that a test can send both well-formed JSON and garbage.
        @Bean
        public ProducerFactory<String, String> testProducerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> p = new HashMap<>();
            p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            p.put(ProducerConfig.ACKS_CONFIG, "all");
            return new DefaultKafkaProducerFactory<>(p);
        }

        @Bean
        public KafkaTemplate<String, String> ordersProducer(
                ProducerFactory<String, String> testProducerFactory) {
            return new KafkaTemplate<>(testProducerFactory);
        }

        // The listener container. Uses the production consumer factory
        // and the production error handler, so this test proves the
        // wiring that main() uses, not a parallel copy.
        @Bean(initMethod = "start", destroyMethod = "stop")
        public org.springframework.kafka.listener.ConcurrentMessageListenerContainer<
                String, EventEnvelope<OrderPlacedPayload>> testContainer(
                KafkaConsumerConfig cfg,
                ObjectMapper mapper,
                org.springframework.kafka.listener.CommonErrorHandler errorHandler,
                StubExecution stub) {

            org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<
                    String, EventEnvelope<OrderPlacedPayload>> factory =
                    cfg.orderPlacedListenerContainerFactory(
                            cfg.orderPlacedConsumerFactory(mapper), errorHandler);

            var container = factory.createContainer(ORDERS_TOPIC);
            container.getContainerProperties().setGroupId(
                    "trade-executor-test-" + UUID.randomUUID());
            // The stub: whatever a test wants the listener to do.
            container.getContainerProperties().setMessageListener(
                    (org.springframework.kafka.listener.AcknowledgingMessageListener<
                            String, EventEnvelope<OrderPlacedPayload>>) (rec, ack) -> {
                        stub.onRecord(rec);
                        ack.acknowledge();
                    });
            return container;
        }
    }

    /**
     * A stand-in for {@link OrderExecutionService} whose behaviour is
     * decided per test. Counts calls and gates a "succeeded once"
     * latch so a test can wait for a positive outcome without sleeping.
     */
    static class StubExecution {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch successLatch = new CountDownLatch(1);
        private final AtomicReference<RuntimeException> failFirstOnce = new AtomicReference<>();

        void reset() {
            calls.set(0);
            failFirstOnce.set(null);
        }

        void failFirst(RuntimeException ex) {
            failFirstOnce.set(ex);
        }

        void onRecord(ConsumerRecord<String, EventEnvelope<OrderPlacedPayload>> rec) {
            calls.incrementAndGet();
            RuntimeException toThrow = failFirstOnce.getAndSet(null);
            if (toThrow != null) {
                throw toThrow;
            }
            successLatch.countDown();
        }

        int callCount() {
            return calls.get();
        }

        boolean awaitSuccess(long timeout, TimeUnit unit) throws InterruptedException {
            return successLatch.await(timeout, unit);
        }
    }
}
