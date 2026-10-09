package com.yellow.trade.outbox;

import com.yellow.trade.mappers.OutboxMapper;
import com.yellow.trade.mappers.OutboxRow;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayTest {

    private static final String FIRST = "6b3a6607-5793-46c4-8635-ae61f90dc84a";
    private static final String SECOND = "0f1e2d3c-4b5a-4968-8776-655443322110";

    private final OutboxMapper outbox = mock(OutboxMapper.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxRelay relay = new OutboxRelay(outbox, kafka, 20);

    private static OutboxRow row(String eventId, String key, int attempts) {
        OutboxRow row = new OutboxRow();
        row.setEventId(eventId);
        row.setTopic("kyc-events");
        row.setMessageKey(key);
        row.setEnvelope("{\"eventId\": \"" + eventId + "\", \"payload\": {\"clientId\": " + key + "}}");
        row.setAttempts(attempts);
        return row;
    }

    @Test
    @DisplayName("Sends each row oldest first, exactly as stored, and marks it published only after the broker has it")
    void publishesInOrder() {
        OutboxRow first = row(FIRST, "11", 0);
        OutboxRow second = row(SECOND, "12", 0);
        when(outbox.lockBatch(20)).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(relay.relayOnce(), is(2));

        InOrder order = inOrder(kafka, outbox);
        order.verify(kafka).send("kyc-events", "11", first.getEnvelope());
        order.verify(outbox).markPublished(FIRST);
        order.verify(kafka).send("kyc-events", "12", second.getEnvelope());
        order.verify(outbox).markPublished(SECOND);
    }

    @Test
    @DisplayName("A failed send is recorded, not marked published, and stops the pass")
    void failureStopsThePass(CapturedOutput output) {
        when(outbox.lockBatch(20)).thenReturn(List.of(row(FIRST, "11", 2), row(SECOND, "12", 0)));
        when(kafka.send("kyc-events", "11", row(FIRST, "11", 2).getEnvelope()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker down")));

        assertThat(relay.relayOnce(), is(0));

        verify(outbox).recordFailure(FIRST, "KafkaException: broker down");
        verify(outbox, never()).markPublished(anyString());
        verify(kafka, never()).send("kyc-events", "12", row(SECOND, "12", 0).getEnvelope());
        assertThat(output.getOut(), containsString(
                "OUTBOX_PUBLISH_FAILED event=" + FIRST + " topic=kyc-events attempts=3 reason=KafkaException: broker down"));
    }

    @Test
    @DisplayName("The error recorded is the root cause, not Spring's \"Failed to send\" wrapper")
    void rootCause() {
        when(outbox.lockBatch(20)).thenReturn(List.of(row(FIRST, "11", 0)));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(
                new KafkaProducerException(new ProducerRecord<>("kyc-events", "11", "{}"), "Failed to send",
                        new TimeoutException("Expiring 1 record(s) for kyc-events-1:15000 ms has passed since batch creation"))));

        relay.relayOnce();

        verify(outbox).recordFailure(FIRST,
                "TimeoutException: Expiring 1 record(s) for kyc-events-1:15000 ms has passed since batch creation");
    }

    @Test
    @DisplayName("A send that throws before returning a future is handled the same way")
    void synchronousFailure() {
        when(outbox.lockBatch(20)).thenReturn(List.of(row(FIRST, "11", 0)));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("Topic kyc-events not present in metadata after 10000 ms."));

        assertThat(relay.relayOnce(), is(0));

        verify(outbox).recordFailure(FIRST, "KafkaException: Topic kyc-events not present in metadata after 10000 ms.");
        verify(outbox, never()).markPublished(anyString());
    }

    @Test
    @DisplayName("Rows published before a failure stay published")
    void partialPass() {
        when(outbox.lockBatch(20)).thenReturn(List.of(row(FIRST, "11", 0), row(SECOND, "12", 0)));
        when(kafka.send("kyc-events", "11", row(FIRST, "11", 0).getEnvelope()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafka.send("kyc-events", "12", row(SECOND, "12", 0).getEnvelope()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker down")));

        assertThat(relay.relayOnce(), is(1));

        verify(outbox).markPublished(FIRST);
        verify(outbox).recordFailure(SECOND, "KafkaException: broker down");
    }

    @Test
    @DisplayName("Nothing to send: no broker call")
    void emptyBatch() {
        when(outbox.lockBatch(20)).thenReturn(List.of());

        assertThat(relay.relayOnce(), is(0));

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }
}
