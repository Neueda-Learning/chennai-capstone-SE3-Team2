package com.yellow.trade.marketdata;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Where prices come from and how long one is kept. Both keys may be empty:
 * the service still starts, and what cannot be priced is answered unpriced.
 * Neither key ever leaves this service.
 */
@Validated
@ConfigurationProperties(prefix = "market-data")
public record MarketDataProperties(
        @NotBlank String fauxnanceBaseUrl,
        String fauxnanceApiKey,
        @NotBlank String mfNavBaseUrl,
        String mfNavApiKey,
        @NotNull Duration timeout,
        /** The NAV service sleeps when idle; its first answer of the day is slow. */
        @NotNull Duration navTimeout,
        @NotNull Duration quoteTtl,
        @NotNull Duration navTtl,
        @NotNull Duration candleTtl,
        /**
         * Fauxnance requests this service may spend a day. The key's 2000 are
         * shared with the executor, whose order pricing must not run dry.
         */
        @Min(0) int fauxnanceDailyBudget) {

    boolean hasFauxnanceKey() {
        return fauxnanceApiKey != null && !fauxnanceApiKey.isBlank();
    }

    /** The keys are never printed, even if this object is. */
    @Override
    public String toString() {
        return "MarketDataProperties[fauxnanceBaseUrl=" + fauxnanceBaseUrl + ", mfNavBaseUrl=" + mfNavBaseUrl
                + ", quoteTtl=" + quoteTtl + ", navTtl=" + navTtl + ", candleTtl=" + candleTtl
                + ", fauxnanceDailyBudget=" + fauxnanceDailyBudget + "]";
    }
}
