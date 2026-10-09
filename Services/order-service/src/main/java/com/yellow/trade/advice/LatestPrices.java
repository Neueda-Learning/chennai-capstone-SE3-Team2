package com.yellow.trade.advice;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.trade.advice.AdviceExceptions.UnreadableQuoteException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The latest price market-data carried for each symbol, in memory. A
 * restart forgets them; the next poll, a minute later, brings them back.
 * Signals read it when they are recomputed, never on the quote itself.
 */
@Component
public class LatestPrices {

    public record Price(BigDecimal price, Instant asOf) {
    }

    private final Map<String, Price> prices = new ConcurrentHashMap<>();

    public Optional<Price> of(String symbol) {
        return Optional.ofNullable(prices.get(symbol));
    }

    /** Held unless one observed later is held already. */
    public void offer(String symbol, BigDecimal price, Instant asOf) {
        prices.merge(symbol, new Price(price, asOf), (held, offered) -> offered.asOf().isBefore(held.asOf()) ? held : offered);
    }

    /** A market-data message, as the poller publishes it (Contracts/API Schemas/kafka-topics.md). */
    public void offer(String message, ObjectMapper json) {
        JsonNode envelope;
        try {
            envelope = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(message);
        } catch (IOException e) {
            throw new UnreadableQuoteException("market-data message is not JSON");
        }
        if (envelope == null || !"QUOTE".equals(envelope.path("eventType").asText(null))) {
            throw new UnreadableQuoteException("market-data message is not a QUOTE");
        }
        JsonNode payload = envelope.path("payload");
        String symbol = payload.path("symbol").asText("");
        JsonNode price = payload.path("price");
        if (symbol.isBlank() || !price.isNumber() || price.decimalValue().signum() <= 0) {
            throw new UnreadableQuoteException("market-data quote has no symbol or no price");
        }
        try {
            offer(symbol, price.decimalValue(), Instant.parse(payload.path("quoteAsOf").asText("")));
        } catch (DateTimeParseException e) {
            throw new UnreadableQuoteException("market-data quoteAsOf is not a date-time");
        }
    }
}
