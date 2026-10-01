package com.yellow.trade.activation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.trade.activation.ActivationExceptions.MalformedEventException;

import java.util.UUID;

/**
 * What the mailer needs from an ACCOUNT_PROVISIONED envelope: the event id (the
 * idempotency key) and the client id. Read from a tree rather than bound to a
 * class, so fields a producer adds later are ignored as the contract requires.
 */
public record AccountProvisionedEvent(String eventId, long clientId) {

    public static final String EVENT_TYPE = "ACCOUNT_PROVISIONED";

    public static AccountProvisionedEvent parse(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            throw new MalformedEventException("empty message");
        }

        JsonNode envelope;
        try {
            envelope = mapper.readTree(json);
        } catch (Exception e) {
            throw new MalformedEventException("not JSON");
        }
        if (envelope == null || !envelope.isObject()) {
            throw new MalformedEventException("envelope is not an object");
        }

        String eventType = envelope.path("eventType").asText("");
        if (!EVENT_TYPE.equals(eventType)) {
            throw new MalformedEventException("unexpected eventType '" + eventType + "'");
        }

        String eventId = envelope.path("eventId").asText("");
        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("eventId is not a UUID");
        }

        JsonNode clientId = envelope.path("payload").path("clientId");
        if (!clientId.isIntegralNumber() || !clientId.canConvertToLong() || clientId.asLong() <= 0) {
            throw new MalformedEventException("payload.clientId is not a positive integer");
        }

        return new AccountProvisionedEvent(eventId, clientId.asLong());
    }
}
