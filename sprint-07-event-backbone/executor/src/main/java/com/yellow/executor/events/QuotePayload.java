package com.yellow.executor.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One quote on {@code market-data}. {@code eventType} is always {@code QUOTE}.
 *
 * <p>TWO TIMESTAMPS, AND THE DIFFERENCE MATTERS. The envelope's
 * {@code eventTime} is when the poller published. {@code quoteAsOf} is when the
 * price was observed. Fauxnance serves delayed quotes, so a strategy service
 * acting on {@code eventTime} is acting on a price that is older than it
 * thinks -- sometimes by a whole trading session, when the market is shut and
 * the last observation is yesterday's close.
 *
 * <p>{@code stale} is carried rather than filtered. The fill path refuses a
 * stale quote outright, because filling a customer's money against a price the
 * API could not refresh is indefensible. A consumer of this topic may
 * reasonably decide otherwise -- a chart would rather draw an old point than a
 * gap -- so the flag travels and the consumer chooses.
 *
 * <p>{@code spreadBps} is not in the contract's field list. It is added here
 * because the contract calls an added optional field a non-breaking change, and
 * because it is the one number that shows the spread model rather than leaving
 * a consumer to infer it from bid and ask. {@code schemaVersion} stays at 1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuotePayload(
        String symbol,
        BigDecimal price,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal spreadBps,
        String currency,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal previousClose,
        String marketState,
        boolean stale,
        Instant quoteAsOf) {
}
