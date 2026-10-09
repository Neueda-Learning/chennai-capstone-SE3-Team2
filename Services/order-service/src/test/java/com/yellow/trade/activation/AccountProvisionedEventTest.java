package com.yellow.trade.activation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.trade.activation.ActivationExceptions.MalformedEventException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountProvisionedEventTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EVENT_ID = "b19d2c5a-8f31-4d0e-9a77-1c3e5f7a9b0d";

    private static String envelope(String eventType, String payload) {
        return """
                {"eventId":"%s","eventType":"%s","eventTime":"2026-09-30T18:22:41Z",
                 "source":"auth-service","schemaVersion":1,"payload":%s}
                """.formatted(EVENT_ID, eventType, payload);
    }

    @Test
    @DisplayName("reads the event id and the numeric client id")
    void readsTheEnvelope() {
        AccountProvisionedEvent event =
                AccountProvisionedEvent.parse(envelope("ACCOUNT_PROVISIONED", "{\"clientId\":7}"), JSON);

        assertThat(event).isEqualTo(new AccountProvisionedEvent(EVENT_ID, 7));
    }

    @Test
    @DisplayName("ignores fields it does not know, as the contract requires")
    void toleratesAdditiveChanges() {
        String json = envelope("ACCOUNT_PROVISIONED", "{\"clientId\":7,\"region\":\"IN\"}")
                .replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"traceId\":\"x\"");

        assertThat(AccountProvisionedEvent.parse(json, JSON).clientId()).isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "not json",
            "[1,2]",
            "{\"eventId\":\"b19d2c5a-8f31-4d0e-9a77-1c3e5f7a9b0d\",\"eventType\":\"ORDER_PLACED\",\"payload\":{\"clientId\":7}}",
            "{\"eventId\":\"not-a-uuid\",\"eventType\":\"ACCOUNT_PROVISIONED\",\"payload\":{\"clientId\":7}}",
            "{\"eventId\":\"b19d2c5a-8f31-4d0e-9a77-1c3e5f7a9b0d\",\"eventType\":\"ACCOUNT_PROVISIONED\",\"payload\":{}}",
            "{\"eventId\":\"b19d2c5a-8f31-4d0e-9a77-1c3e5f7a9b0d\",\"eventType\":\"ACCOUNT_PROVISIONED\",\"payload\":{\"clientId\":\"7\"}}",
            "{\"eventId\":\"b19d2c5a-8f31-4d0e-9a77-1c3e5f7a9b0d\",\"eventType\":\"ACCOUNT_PROVISIONED\",\"payload\":{\"clientId\":0}}",
            "{\"eventId\":\"b19d2c5a-8f31-4d0e-9a77-1c3e5f7a9b0d\",\"eventType\":\"ACCOUNT_PROVISIONED\",\"payload\":{\"clientId\":1.5}}"
    })
    @DisplayName("anything it cannot read is poison")
    void malformedIsPoison(String json) {
        assertThatThrownBy(() -> AccountProvisionedEvent.parse(json, JSON))
                .isInstanceOf(MalformedEventException.class);
    }
}
