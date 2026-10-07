package com.yellow.trade.watchlists;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * One quote from market-data (contracts/kafka-topics.md), as much of it as
 * this module uses. Fields it does not know are ignored.
 *
 * @param eventId   the envelope's eventId, carried to notifications with each alert it fires
 * @param quoteAsOf when Fauxnance observed the price, not when it was published
 */
public record MarketQuote(UUID eventId, String symbol, BigDecimal price, BigDecimal changePercent, boolean stale,
                          Instant quoteAsOf) {

    /** Something on market-data that can never be a quote. Dead-lettered on the first attempt. */
    public static final class UnreadableQuoteException extends RuntimeException {
        UnreadableQuoteException(String message) {
            super(message);
        }
    }

    public static MarketQuote parse(String value, ObjectMapper json) {
        JsonNode envelope;
        try {
            // Prices exactly as written: a threshold is compared with them.
            envelope = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(value);
        } catch (IOException e) {
            throw new UnreadableQuoteException("market-data message is not JSON");
        }
        if (envelope == null || !envelope.isObject() || !"QUOTE".equals(envelope.path("eventType").asText(null))) {
            throw new UnreadableQuoteException("market-data message is not a QUOTE");
        }
        JsonNode payload = envelope.path("payload");
        JsonNode price = payload.path("price");
        if (!price.isNumber() || price.decimalValue().signum() <= 0) {
            throw new UnreadableQuoteException("market-data quote has no price");
        }
        String symbol = payload.path("symbol").asText("");
        if (symbol.isBlank()) {
            throw new UnreadableQuoteException("market-data quote has no symbol");
        }
        JsonNode change = payload.path("changePercent");
        return new MarketQuote(
                uuid(envelope.path("eventId").asText("")),
                symbol,
                price.decimalValue(),
                change.isNumber() ? change.decimalValue() : null,
                payload.path("stale").asBoolean(false),
                instant(payload.path("quoteAsOf").asText("")));
    }

    private static UUID uuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new UnreadableQuoteException("market-data eventId is not a UUID");
        }
    }

    private static Instant instant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new UnreadableQuoteException("market-data quoteAsOf is not a date-time");
        }
    }
}
