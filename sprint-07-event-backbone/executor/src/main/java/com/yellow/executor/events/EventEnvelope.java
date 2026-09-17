package com.yellow.executor.events;

import java.time.Instant;

/**
 * The five-field envelope every message on every topic carries.
 *
 * <p>Identical on orders, trade-events and market-data by contract, so that one
 * deserialiser and one dead-letter handler cover the platform. The Trade REST
 * API produces the same shape; this is the consuming side of it.
 *
 * @param eventId       unique per message. The idempotency key for consumers
 * @param eventType     discriminates the payload
 * @param eventTime     when the PRODUCER created the event, not when we read it
 * @param source        the producing component: trade-api, trade-executor, market-poller
 * @param schemaVersion 1. Bumped only on a breaking change, never for an added field
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        Instant eventTime,
        String source,
        int schemaVersion,
        T payload) {
}
