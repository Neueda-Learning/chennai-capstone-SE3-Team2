package com.yellow.trade.payments;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** The payment job's settings. See application.yml. */
@Validated
@ConfigurationProperties(prefix = "payments")
public record PaymentProperties(
        /** How long a transfer waits before the gateway decides it: a real gateway is not instant. */
        @NotNull Duration delay,
        /** How many transfers one run of the job takes on. */
        @Min(1) int batchSize,
        /** Failed attempts to decide before a transfer is set aside for a person to look at. */
        @Min(1) int maxAttempts) {
}
