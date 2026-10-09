package com.yellow.trade.kyc;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** The KYC job's settings. See application.yml. */
@Validated
@ConfigurationProperties(prefix = "kyc")
public record KycProperties(
        /** Fixed by Contracts/API Schemas/kafka-topics.md. */
        @NotBlank String topic,
        /** How long a check waits before it runs: a real provider is not instant. */
        @NotNull Duration delay,
        /** How many checks one run of the job takes on. */
        @Min(1) int batchSize,
        /** Failed checks before a customer is set aside for a person to look at. */
        @Min(1) int maxAttempts) {
}
