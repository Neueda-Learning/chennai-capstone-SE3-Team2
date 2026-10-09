package com.yellow.trade.strategy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * One quote from market-data (Contracts/API Schemas/kafka-topics.md), as much as a
 * strategy needs: the price that decides a crossing, and the bid and ask its
 * order is priced from.
 */
record StrategyQuote(UUID eventId, String symbol, BigDecimal price, BigDecimal bid, BigDecimal ask, Instant quoteAsOf) {

    static StrategyQuote parse(String value, ObjectMapper json) {
        JsonNode envelope;
        try {
            envelope = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(value);
        } catch (IOException e) {
            throw new StrategyExceptions.UnreadableEventException("market-data message is not JSON");
        }
        if (envelope == null || !"QUOTE".equals(envelope.path("eventType").asText(null))) {
            throw new StrategyExceptions.UnreadableEventException("market-data message is not a QUOTE");
        }
        JsonNode payload = envelope.path("payload");
        JsonNode price = payload.path("price");
        String symbol = payload.path("symbol").asText("");
        if (symbol.isBlank() || !price.isNumber() || price.decimalValue().signum() <= 0) {
            throw new StrategyExceptions.UnreadableEventException("market-data quote has no symbol or no price");
        }
        try {
            return new StrategyQuote(UUID.fromString(envelope.path("eventId").asText("")), symbol, price.decimalValue(),
                    positiveOr(payload.path("bid"), price.decimalValue()), positiveOr(payload.path("ask"), price.decimalValue()),
                    Instant.parse(payload.path("quoteAsOf").asText("")));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new StrategyExceptions.UnreadableEventException("market-data quote has no eventId or no quoteAsOf");
        }
    }

    private static BigDecimal positiveOr(JsonNode node, BigDecimal fallback) {
        return node.isNumber() && node.decimalValue().signum() > 0 ? node.decimalValue() : fallback;
    }
}
