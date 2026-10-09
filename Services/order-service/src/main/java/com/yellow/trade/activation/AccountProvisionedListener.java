package com.yellow.trade.activation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes account-provisioning, published by auth once an account exists.
 * This is not Sprint 10's notification module: a different topic, a different
 * group and a different purpose. It never reads trade-events.
 */
@Component
public class AccountProvisionedListener {

    private final ActivationService activation;
    private final ObjectMapper json;

    public AccountProvisionedListener(ActivationService activation, ObjectMapper json) {
        this.activation = activation;
        this.json = json;
    }

    @KafkaListener(
            id = "activation-mailer",
            topics = "${activation.topic}",
            groupId = "${activation.consumer-group}",
            containerFactory = "activationListenerContainerFactory",
            autoStartup = "${activation.consumer.auto-startup:true}")
    public void onAccountProvisioned(String value) {
        activation.handle(AccountProvisionedEvent.parse(value, json));
    }
}
