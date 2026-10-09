package com.yellow.trade.services;

import com.yellow.entities.Order;
import com.yellow.enums.OrderSide;
import com.yellow.trade.events.EventEnvelope;
import com.yellow.trade.events.OrderPlacedDomainEvent;
import com.yellow.trade.events.OrderPlacedEvent;
import com.yellow.trade.config.KafkaTopics;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    private static final Long ACCOUNT = 3L;
    private static final Long INSTRUMENT = 1L;

    @Mock private KafkaTemplate<String, EventEnvelope<OrderPlacedEvent>> kafkaTemplate;
    @Mock private InstrumentMapper instrumentMapper;

    @Captor private ArgumentCaptor<EventEnvelope<OrderPlacedEvent>> envelopeCaptor;

    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafkaTemplate, instrumentMapper);

        InstrumentRow instrument = new InstrumentRow();
        instrument.setInstrumentId(INSTRUMENT);
        instrument.setSymbol("APEX");
        when(instrumentMapper.findById(INSTRUMENT)).thenReturn(instrument);
    }

    @Test
    @DisplayName("ORDER_PLACED is published to Kafka with correct payload")
    void publishesOrderPlacedEvent() {
        Order order = Order.place(ACCOUNT, INSTRUMENT, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("1450.00"), "key-12345678");
        OrderPlacedDomainEvent event = new OrderPlacedDomainEvent(this, order);

        publisher.publishOrderPlaced(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopics.ORDERS),
                eq(ACCOUNT.toString()),
                envelopeCaptor.capture());
        
        EventEnvelope<OrderPlacedEvent> envelope = envelopeCaptor.getValue();
        assertThat(envelope.eventId(), notNullValue());
        assertThat(envelope.eventType(), is("ORDER_PLACED"));
        assertThat(envelope.source(), is("trade-api"));
        assertThat(envelope.schemaVersion(), is(1));
        assertThat(envelope.eventTime(), notNullValue());
        
        OrderPlacedEvent payload = envelope.payload();
        assertThat(payload.accountId(), is(ACCOUNT));
        assertThat(payload.symbol(), is("APEX"));
        assertThat(payload.side(), is(OrderSide.BUY));
        assertThat(payload.quantity(), comparesEqualTo(new BigDecimal("10")));
        assertThat(payload.price(), comparesEqualTo(new BigDecimal("1450.00")));
        assertThat(payload.status(), is("NEW"));
        assertThat(payload.createdOn(), notNullValue());
        assertThat(payload.idempotencyKey(), is("key-12345678"));
    }

    @Test
    @DisplayName("event is keyed by account ID for partition assignment")
    void messageKeyedByAccountId() {
        Order order = Order.place(ACCOUNT, INSTRUMENT, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("1450.00"), "key-12345678");
        OrderPlacedDomainEvent event = new OrderPlacedDomainEvent(this, order);

        publisher.publishOrderPlaced(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopics.ORDERS),
                eq(ACCOUNT.toString()),
                any(EventEnvelope.class));
    }

    @Test
    @DisplayName("message is sent to orders topic")
    void messageTopicIsOrders() {
        Order order = Order.place(ACCOUNT, INSTRUMENT, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("1450.00"), "key-12345678");
        OrderPlacedDomainEvent event = new OrderPlacedDomainEvent(this, order);

        publisher.publishOrderPlaced(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopics.ORDERS),
                anyString(),
                any(EventEnvelope.class));
    }

    @Test
    @DisplayName("failure to publish is logged but does not throw")
    void publishFailureIsLoggedNotThrown() {
        Order order = Order.place(ACCOUNT, INSTRUMENT, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("1450.00"), "key-12345678");
        OrderPlacedDomainEvent event = new OrderPlacedDomainEvent(this, order);
        
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(
                new RuntimeException("Kafka broker unavailable"));

        // Should not throw
        publisher.publishOrderPlaced(event);

        // Verify send was attempted
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("null instrument is handled gracefully")
    void nullInstrumentIsHandled() {
        when(instrumentMapper.findById(INSTRUMENT)).thenReturn(null);

        Order order = Order.place(ACCOUNT, INSTRUMENT, OrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("1450.00"), "key-12345678");
        OrderPlacedDomainEvent event = new OrderPlacedDomainEvent(this, order);

        publisher.publishOrderPlaced(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopics.ORDERS),
                eq(ACCOUNT.toString()),
                envelopeCaptor.capture());
        
        EventEnvelope<OrderPlacedEvent> envelope = envelopeCaptor.getValue();
        OrderPlacedEvent payload = envelope.payload();
        assertThat(payload.symbol(), is((String) null));
        assertThat(payload.orderId(), is(notNullValue()));
    }
}

