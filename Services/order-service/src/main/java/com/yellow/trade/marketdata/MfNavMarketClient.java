package com.yellow.trade.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The MF NAV service, for the screens: the latest NAV of up to 25 funds in one
 * request, asked for by AMFI scheme code. A NAV has no bid, ask or day change.
 */
@Component
public class MfNavMarketClient {

    static final int MAX_BATCH = 25;

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final RestClient http;

    public MfNavMarketClient(RestClient.Builder builder, MarketDataProperties properties) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(properties.navTimeout());
        timeouts.setReadTimeout(properties.navTimeout());
        RestClient.Builder configured = builder.baseUrl(properties.mfNavBaseUrl()).requestFactory(timeouts);
        if (properties.mfNavApiKey() != null && !properties.mfNavApiKey().isBlank()) {
            configured.defaultHeader("X-Api-Key", properties.mfNavApiKey());
        }
        this.http = configured.build();
    }

    /** Keyed by the scheme code asked for. A fund the service does not know is left out. */
    public Map<String, PriceQuote> navs(List<String> schemeCodes) {
        if (schemeCodes.size() > MAX_BATCH) {
            throw new IllegalArgumentException("at most " + MAX_BATCH + " funds a request, asked for " + schemeCodes.size());
        }
        Map<String, PriceQuote> found = new LinkedHashMap<>();
        if (schemeCodes.isEmpty()) {
            return found;
        }
        JsonNode body;
        try {
            body = http.get().uri("/nav?isins={codes}", String.join(",", schemeCodes))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) {
                            throw new PricingUnavailableException("the MF NAV service answered " + status);
                        }
                        return response.bodyTo(JsonNode.class);
                    });
        } catch (ResourceAccessException e) {
            throw new PricingUnavailableException("the MF NAV service is unreachable", e);
        }

        for (JsonNode item : body.path("data").path("navs")) {
            JsonNode nav = item.path("nav");
            if (item.hasNonNull("error") || !nav.path("nav").isNumber()) {
                continue;
            }
            String code = item.path("isin").asText();
            // A NAV is struck for a date, not a moment: midnight of that date in India.
            LocalDate navDate = LocalDate.parse(nav.path("navDate").asText());
            found.put(code, new PriceQuote(code, nav.path("nav").decimalValue(), null, null, null, null, null,
                    "INR", navDate.atStartOfDay(INDIA).toInstant(), item.path("stale").asBoolean(false)));
        }
        return found;
    }
}
