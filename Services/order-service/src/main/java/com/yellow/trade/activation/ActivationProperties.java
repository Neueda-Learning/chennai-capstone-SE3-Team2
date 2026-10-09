package com.yellow.trade.activation;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The activation mailer's settings. The internal secret and the sender address
 * have no defaults (see application.yml): the service refuses to start without
 * them rather than calling auth unauthenticated or mailing from nowhere.
 */
@Validated
@ConfigurationProperties(prefix = "activation")
public record ActivationProperties(
        @NotBlank String topic,
        /** Names this logical consumer and is shared with nothing else. */
        @NotBlank String consumerGroup,
        @NotBlank String authBaseUrl,
        @NotBlank String internalSecret,
        /** The page the link opens; the token is appended as ?token=. */
        @NotBlank String linkBaseUrl,
        @NotBlank String mailFrom) {

    /** The secret is never printed, even if this object is. */
    @Override
    public String toString() {
        return "ActivationProperties[topic=" + topic + ", consumerGroup=" + consumerGroup
                + ", authBaseUrl=" + authBaseUrl + ", linkBaseUrl=" + linkBaseUrl + "]";
    }
}
