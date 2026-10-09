package com.yellow.executor.events;

import java.time.Instant;

/** The five-field envelope every message on every topic carries. */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        Instant eventTime,
        String source,
        int schemaVersion,
        T payload) {
}
