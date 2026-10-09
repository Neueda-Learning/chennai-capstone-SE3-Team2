package com.yellow.trade.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.trade.notifications.NotificationExceptions.UnreadableEventException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

/**
 * One order outcome from trade-events, as much of it as a message needs. Read
 * from the contract's envelope (Contracts/API Schemas/kafka-topics.md); fields it does not
 * know are ignored, as the contract asks of every consumer.
 *
 * @param eventId       the envelope's eventId: what makes a replay a no-op
 * @param executedPrice the price a fill was priced at; null on a rejection or a cancellation
 * @param reason        why it was rejected or cancelled, as the producer names it; null on a fill
 */
public record TradeEvent(UUID eventId, NotificationKind kind, Instant eventTime, UUID orderId, long accountId,
                         String symbol, String side, BigDecimal quantity, BigDecimal price,
                         BigDecimal executedPrice, String reason) {

    private static final Set<String> SIDES = Set.of("BUY", "SELL");

    public static TradeEvent parse(String value, ObjectMapper json) {
        JsonNode envelope;
        try {
            envelope = json.readTree(value);
        } catch (IOException e) {
            throw new UnreadableEventException("trade-events message is not JSON", e);
        }
        if (envelope == null || !envelope.isObject()) {
            throw new UnreadableEventException("trade-events message is not a JSON object");
        }
        NotificationKind kind = kind(text(envelope, "eventType"));
        JsonNode payload = envelope.path("payload");
        if (!payload.isObject()) {
            throw new UnreadableEventException("trade-events message has no payload");
        }

        String side = text(payload, "side");
        if (!SIDES.contains(side)) {
            throw new UnreadableEventException("trade-events payload side is not BUY or SELL");
        }
        BigDecimal executedPrice = decimal(payload, "executedPrice", kind != NotificationKind.ORDER_FILLED);
        if (kind == NotificationKind.ORDER_FILLED && executedPrice == null) {
            throw new UnreadableEventException("a fill on trade-events has no executedPrice");
        }
        JsonNode accountId = payload.path("accountId");
        if (!accountId.canConvertToLong()) {
            throw new UnreadableEventException("trade-events payload has no accountId");
        }
        return new TradeEvent(
                uuid(envelope, "eventId"),
                kind,
                instant(envelope, "eventTime"),
                uuid(payload, "orderId"),
                accountId.asLong(),
                text(payload, "symbol"),
                side,
                decimal(payload, "quantity", false),
                decimal(payload, "price", true),
                executedPrice,
                payload.path("reason").isTextual() ? payload.path("reason").asText() : null);
    }

    private static NotificationKind kind(String eventType) {
        return switch (eventType) {
            case "ORDER_FILLED" -> NotificationKind.ORDER_FILLED;
            case "ORDER_REJECTED" -> NotificationKind.ORDER_REJECTED;
            case "ORDER_CANCELLED" -> NotificationKind.ORDER_CANCELLED;
            default -> throw new UnreadableEventException("trade-events does not carry this eventType");
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new UnreadableEventException("trade-events message has no " + field);
        }
        return value.asText();
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(text(node, field));
        } catch (IllegalArgumentException e) {
            throw new UnreadableEventException("trade-events " + field + " is not a UUID");
        }
    }

    private static Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(text(node, field));
        } catch (DateTimeParseException e) {
            throw new UnreadableEventException("trade-events " + field + " is not a date-time");
        }
    }

    private static BigDecimal decimal(JsonNode node, String field, boolean optional) {
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (optional && (value.isNull() || value.isMissingNode())) {
            return null;
        }
        throw new UnreadableEventException("trade-events payload has no " + field);
    }
}
