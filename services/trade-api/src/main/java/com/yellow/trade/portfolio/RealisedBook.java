package com.yellow.trade.portfolio;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.OrderRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Realised profit and loss, booked from trade-events at the moment of each
 * sale and never recomputed (decision log 0011):
 * (sale price - average cost at the sale) * quantity sold.
 *
 * The average cost is the event's averageCostAfter: a sale never changes it,
 * and when a sale closes a position the executor sends the cost the units
 * were held at. Booked only for a sell the platform's orders table recorded
 * as filled on that account, so a hand-published or forged event never
 * reaches a customer's figures. Keyed on the event, and on the order.
 */
@Service
public class RealisedBook {

    private static final Logger log = LoggerFactory.getLogger(RealisedBook.class);
    private static final int SCALE = 4;

    /** A trade-events message that can never be read. Dead-lettered on the first attempt. */
    public static final class UnreadableEventException extends RuntimeException {
        UnreadableEventException(String message) {
            super(message);
        }
    }

    private final RealisedMapper realised;
    private final OrderMapper orders;
    private final ObjectMapper json;

    public RealisedBook(RealisedMapper realised, OrderMapper orders, ObjectMapper json) {
        this.realised = realised;
        this.orders = orders;
        this.json = json;
    }

    /** @return true when a sale was booked; false for anything else, a replay included */
    @Transactional
    public boolean book(String value) {
        JsonNode envelope = read(value);
        UUID eventId = uuid(envelope.path("eventId"), "eventId");
        JsonNode payload = envelope.path("payload");
        if (!"ORDER_FILLED".equals(envelope.path("eventType").asText()) || !"SELL".equals(payload.path("side").asText())) {
            return false;
        }
        UUID orderId = uuid(payload.path("orderId"), "orderId");
        long accountId = payload.path("accountId").asLong(-1);

        OrderRow order = orders.findById(orderId);
        if (order == null || order.getClientId() == null || order.getClientId() != accountId
                || order.getSide() != OrderSide.SELL || order.getStatus() != OrderStatus.FILLED) {
            log.warn("trade event {}: no filled sale {} on account {} in orders, so nothing is booked",
                    eventId, orderId, accountId);
            return false;
        }
        BigDecimal salePrice = decimal(payload.path("executedPrice"));
        BigDecimal averageCost = decimal(payload.path("averageCostAfter"));
        BigDecimal quantity = decimal(payload.path("quantity"));
        if (salePrice == null || averageCost == null || quantity == null || quantity.signum() <= 0) {
            log.warn("trade event {}: sale {} has no price, average cost or quantity, so nothing is booked",
                    eventId, orderId);
            return false;
        }

        RealisedRow row = new RealisedRow();
        row.setEventId(eventId);
        row.setOrderId(orderId);
        row.setClientId(accountId);
        row.setInstrumentId(order.getInstrumentId());
        row.setSymbol(payload.path("symbol").asText());
        row.setQuantity(quantity);
        row.setSalePrice(salePrice);
        row.setAverageCost(averageCost);
        row.setRealised(salePrice.subtract(averageCost).multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP));
        row.setBookedAt(instant(payload.path("executedOn"), envelope.path("eventTime")));

        if (realised.insert(row) == 0) {
            log.info("trade event {}: sale {} already booked", eventId, orderId);
            return false;
        }
        log.info("realised {} booked for sale {} on account {}", row.getRealised(), orderId, accountId);
        return true;
    }

    private JsonNode read(String value) {
        try {
            JsonNode envelope = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(value);
            if (envelope == null || !envelope.isObject()) {
                throw new UnreadableEventException("trade-events message is not a JSON object");
            }
            return envelope;
        } catch (IOException e) {
            throw new UnreadableEventException("trade-events message is not JSON");
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(node.asText(""));
        } catch (IllegalArgumentException e) {
            throw new UnreadableEventException("trade-events " + field + " is not a UUID");
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : null;
    }

    /** When the sale happened: executedOn, else the event's own time. */
    private static Instant instant(JsonNode executedOn, JsonNode eventTime) {
        for (JsonNode node : new JsonNode[] {executedOn, eventTime}) {
            try {
                if (node.isTextual()) {
                    return Instant.parse(node.asText());
                }
            } catch (DateTimeParseException e) {
                // try the next
            }
        }
        throw new UnreadableEventException("trade-events message has no time");
    }
}
