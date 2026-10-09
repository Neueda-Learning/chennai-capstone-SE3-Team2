package com.yellow.trade.strategy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

/**
 * What happened to a strategy's order, from trade-events: a FILLED or a
 * REJECTED run. A rejection is a failure: counted, the strategy armed again
 * to try at its next quote, STOPPED at the third. A customer cancelling the
 * order is recorded and counts against nothing. Keyed on the event: a replay
 * records nothing twice and counts nothing twice.
 */
@Service
public class StrategyOutcomes {

    private static final Logger log = LoggerFactory.getLogger(StrategyOutcomes.class);

    private final StrategyMapper strategies;
    private final ObjectMapper json;
    private final Clock clock;

    public StrategyOutcomes(StrategyMapper strategies, ObjectMapper json, Clock clock) {
        this.strategies = strategies;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public void apply(String value) {
        JsonNode envelope;
        try {
            envelope = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(value);
        } catch (IOException e) {
            throw new StrategyExceptions.UnreadableEventException("trade-events message is not JSON");
        }
        UUID eventId;
        UUID orderId;
        try {
            eventId = UUID.fromString(envelope.path("eventId").asText(""));
            orderId = UUID.fromString(envelope.path("payload").path("orderId").asText(""));
        } catch (IllegalArgumentException e) {
            throw new StrategyExceptions.UnreadableEventException("trade-events message has no eventId or orderId");
        }
        Long strategyId = strategies.findByPlacedOrder(orderId);
        if (strategyId == null) {
            return;
        }
        JsonNode payload = envelope.path("payload");
        String reason = payload.path("reason").isTextual() ? payload.path("reason").asText() : null;
        BigDecimal executed = payload.path("executedPrice").isNumber() ? payload.path("executedPrice").decimalValue() : null;
        switch (envelope.path("eventType").asText("")) {
            case "ORDER_FILLED" -> record(strategyId, RunOutcome.FILLED, executed, null, orderId, eventId);
            case "ORDER_REJECTED" -> {
                if (record(strategyId, RunOutcome.REJECTED, null, reason, orderId, eventId)) {
                    Integer failures = strategies.recordFailure(strategyId);
                    log.warn("strategy {}: order {} rejected ({}), failure {}", strategyId, orderId, reason, failures);
                    if (failures != null && failures >= 3) {
                        record(strategyId, RunOutcome.STOPPED, null,
                                "Stopped after three failures; switch it on again to re-arm it.", null,
                                UUID.nameUUIDFromBytes(("stopped:" + eventId).getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
            case "ORDER_CANCELLED" -> record(strategyId, RunOutcome.REJECTED, null, "The order was cancelled.", orderId, eventId);
            default -> throw new StrategyExceptions.UnreadableEventException("trade-events does not carry this eventType");
        }
    }

    private boolean record(long strategyId, RunOutcome outcome, BigDecimal price, String reason, UUID orderId, UUID eventId) {
        return strategies.insertRun(RunRow.of(strategyId, outcome, price, reason, orderId, eventId, clock.instant())) > 0;
    }
}
