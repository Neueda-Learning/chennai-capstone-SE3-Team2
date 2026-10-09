package com.yellow.trade.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Generic envelope wrapping all Kafka event payloads across the platform. */
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
