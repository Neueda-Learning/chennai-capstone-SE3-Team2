package com.yellow.trade.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Generic envelope wrapping all Kafka event payloads across the platform.
 * Every message on every topic carries this five-field envelope, enabling
 * a single deserializer and dead-letter handler to cover all topics.
 *
 * @param <T> The payload type (e.g., OrderPlacedEvent)
 */
public record EventEnvelope<T>(
        @JsonProperty("eventId")
        String eventId,

        @JsonProperty("eventType")
        String eventType,

        @JsonProperty("eventTime")
        Instant eventTime,

        @JsonProperty("source")
        String source,

        @JsonProperty("schemaVersion")
        int schemaVersion,

        @JsonProperty("payload")
        T payload) {
}
