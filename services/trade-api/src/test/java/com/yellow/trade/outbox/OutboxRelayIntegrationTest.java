package com.yellow.trade.outbox;

import com.yellow.trade.integration.PostgresSupport;
import com.yellow.trade.mappers.OutboxMapper;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The relay's SQL against the real schema, with the broker mocked. The relay
 * job is switched off here (PostgresSupport); each test runs a pass by hand,
 * in a transaction as the proxy would.
 */
@SpringBootTest
@EnabledIf(
        value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable",
        disabledReason = "needs a database: start Docker, or set IT_DB_URL "
                + "(with IT_DB_USER and IT_DB_PASSWORD) to a PostgreSQL you already have")
class OutboxRelayIntegrationTest extends PostgresSupport {

    private static final String FIRST = "6b3a6607-5793-46c4-8635-ae61f90dc84a";
    private static final String SECOND = "0f1e2d3c-4b5a-4968-8776-655443322110";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OutboxMapper outbox;
    @Autowired private TransactionTemplate transactions;

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private OutboxRelay relay;

    @BeforeEach
    void rebuildDatabase() {
        applySchema(jdbc);
        relay = new OutboxRelay(outbox, kafka, 20);
    }

    private void queue(String eventId, String key, String createdAt) {
        outbox.insert(eventId, "kyc-events", key,
                "{\"eventId\":\"" + eventId + "\",\"payload\":{\"clientId\":" + key + "}}");
        jdbc.update("UPDATE outbox_event SET created_at = ?::timestamptz WHERE event_id = ?::uuid", createdAt, eventId);
    }

    private int pass() {
        return transactions.execute(status -> relay.relayOnce());
    }

    private Map<String, Object> row(String eventId) {
        return jdbc.queryForMap("SELECT published_at, attempts, last_error FROM outbox_event WHERE event_id = ?::uuid",
                eventId);
    }

    @Test
    @DisplayName("Publishes oldest first, sends the envelope as stored, and marks each row published")
    void publishes() {
        queue(SECOND, "12", "2026-10-02T09:00:02Z");
        queue(FIRST, "11", "2026-10-02T09:00:01Z");
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(pass(), is(2));

        // jsonb's own text form: what was stored, key order and spacing aside.
        verify(kafka).send("kyc-events", "11", "{\"eventId\": \"" + FIRST + "\", \"payload\": {\"clientId\": 11}}");
        assertThat(row(FIRST).get("published_at"), is(notNullValue()));
        assertThat(row(SECOND).get("published_at"), is(notNullValue()));

        // Published rows are not sent again.
        assertThat(pass(), is(0));
    }

    @Test
    @DisplayName("A failure counts the attempt, keeps the error, and leaves the row and the rest for the next pass")
    void failureIsRecorded() {
        queue(FIRST, "11", "2026-10-02T09:00:01Z");
        queue(SECOND, "12", "2026-10-02T09:00:02Z");
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker down")));

        assertThat(pass(), is(0));

        Map<String, Object> failed = row(FIRST);
        assertThat(failed.get("published_at"), is(nullValue()));
        assertThat(failed.get("attempts"), is(1));
        assertThat(failed.get("last_error"), is("KafkaException: broker down"));
        assertThat(row(SECOND).get("attempts"), is(0));

        // The broker comes back: the next pass sends both and clears the error.
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        assertThat(pass(), is(2));
        assertThat(row(FIRST).get("last_error"), is(nullValue()));
        assertThat(row(FIRST).get("attempts"), is(1));
    }

    @Test
    @DisplayName("A row another instance has locked is skipped, not waited on and not sent twice")
    void skipsLockedRows() throws Exception {
        queue(FIRST, "11", "2026-10-02T09:00:01Z");
        queue(SECOND, "12", "2026-10-02T09:00:02Z");
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            // Another instance, partway through sending the oldest row.
            Future<?> holder = other.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.queryForList("SELECT event_id FROM outbox_event WHERE event_id = ?::uuid FOR UPDATE", FIRST);
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS), is(true));

            assertThat(pass(), is(1));

            verify(kafka, never()).send(eq("kyc-events"), eq("11"), anyString());
            assertThat(row(SECOND).get("published_at"), is(notNullValue()));
            assertThat(row(FIRST).get("published_at"), is(nullValue()));

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            other.shutdownNow();
        }
    }
}
