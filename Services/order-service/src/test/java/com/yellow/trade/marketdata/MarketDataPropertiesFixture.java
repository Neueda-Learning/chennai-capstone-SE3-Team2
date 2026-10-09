package com.yellow.trade.marketdata;

import java.time.Duration;

/** Properties for tests: short timeouts, the production cache lifetimes, a budget of 400. */
final class MarketDataPropertiesFixture {

    private MarketDataPropertiesFixture() {
    }

    static MarketDataProperties with(String fauxnanceBaseUrl, String fauxnanceApiKey,
                                     String mfNavBaseUrl, String mfNavApiKey) {
        return budget(fauxnanceBaseUrl, fauxnanceApiKey, mfNavBaseUrl, mfNavApiKey, 400);
    }

    static MarketDataProperties budget(String fauxnanceBaseUrl, String fauxnanceApiKey,
                                       String mfNavBaseUrl, String mfNavApiKey, int dailyBudget) {
        return new MarketDataProperties(fauxnanceBaseUrl, fauxnanceApiKey, mfNavBaseUrl, mfNavApiKey,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofSeconds(60), Duration.ofMinutes(30), Duration.ofHours(6), dailyBudget);
    }
}
