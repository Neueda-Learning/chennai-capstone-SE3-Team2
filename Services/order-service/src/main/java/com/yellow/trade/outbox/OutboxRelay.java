package com.yellow.trade.outbox;

import com.yellow.trade.mappers.OutboxMapper;
import com.yellow.trade.mappers.OutboxRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes committed outbox rows, oldest first. The same design as the auth
 * service's relay, so the two outboxes behave alike.
 *
 * It only ever sees committed rows, which is what makes every event "after the
 * commit". A row is marked published only once the broker has acknowledged it,
 * so a crash between the two sends it again: consumers are idempotent on
 * eventId, and auth's provisioning on clientId too.
 *
 * A row that fails is retried on every pass, without limit. Giving up on a
 * KYC_VERIFIED would leave a verified customer without a login; the
 * OUTBOX_PUBLISH_FAILED line and the row's attempts say it is stuck.
 */
@Service
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /** The log line to alert on: an announcement that the broker has not yet taken. */
    static final String OUTBOX_PUBLISH_FAILED = "OUTBOX_PUBLISH_FAILED";

    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxMapper outbox;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;

    public OutboxRelay(OutboxMapper outbox,
                       @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> kafka,
                       @Value("${outbox.relay.batch-size:20}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.batchSize = batchSize;
    }

    /**
     * One pass. The batch stays locked until it ends, and a failure is
     * recorded in the same transaction.
     *
     * @return how many rows were published
     */
    @Transactional
    public int relayOnce() {
        List<OutboxRow> batch = outbox.lockBatch(batchSize);

        int published = 0;
        for (OutboxRow row : batch) {
            try {
                send(row);
            } catch (Exception e) {
                String error = describe(e);
                outbox.recordFailure(row.getEventId(), error);
                log.warn("{} event={} topic={} attempts={} reason={}",
                        OUTBOX_PUBLISH_FAILED, row.getEventId(), row.getTopic(), row.getAttempts() + 1, error);
                // The broker is the likely cause; the rest of the batch would
                // fail the same way, each one waiting out the timeout.
                break;
            }
            outbox.markPublished(row.getEventId());
            published++;
        }
        return published;
    }

    private void send(OutboxRow row) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            kafka.send(row.getTopic(), row.getMessageKey(), row.getEnvelope())
                    .get(OutboxKafkaConfig.SEND_TIMEOUT_MS + 5_000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    /**
     * The root cause's class and message: Spring's KafkaProducerException only
     * says "Failed to send". A Kafka error names the topic and the broker,
     * never the record, and the record holds a client id only.
     */
    private static String describe(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String text = cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
        return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }
}
