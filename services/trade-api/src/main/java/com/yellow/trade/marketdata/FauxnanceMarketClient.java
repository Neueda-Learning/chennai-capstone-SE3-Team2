package com.yellow.trade.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fauxnance, for the screens: batched latest quotes and daily candles. The
 * key travels in a header, never in a URL, and never reaches the browser.
 * Every failure is a PricingUnavailableException; deciding what to serve
 * instead is the caller's business.
 */
@Component
public class FauxnanceMarketClient {

    /** The batch endpoint's hard cap: 26 symbols is a 400, not a truncation. */
    static final int MAX_BATCH = 25;

    private final RestClient http;

    public FauxnanceMarketClient(RestClient.Builder builder, MarketDataProperties properties) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(properties.timeout());
        timeouts.setReadTimeout(properties.timeout());
        RestClient.Builder configured = builder.baseUrl(properties.fauxnanceBaseUrl()).requestFactory(timeouts);
        if (properties.hasFauxnanceKey()) {
            configured.defaultHeader("X-Api-Key", properties.fauxnanceApiKey());
        }
        this.http = configured.build();
    }

    /** Up to 25 symbols in one request. A symbol Fauxnance does not know is left out. */
    public Map<String, PriceQuote> quotes(List<String> symbols) {
        if (symbols.size() > MAX_BATCH) {
            throw new IllegalArgumentException("at most " + MAX_BATCH + " symbols a request, asked for " + symbols.size());
        }
        Map<String, PriceQuote> found = new LinkedHashMap<>();
        if (symbols.isEmpty()) {
            return found;
        }
        JsonNode body = get("/quotes?symbols={symbols}", String.join(",", symbols));
        for (JsonNode item : body.path("data").path("quotes")) {
            if (item.hasNonNull("error") || !item.hasNonNull("quote")) {
                continue;
            }
            JsonNode q = item.path("quote");
            String symbol = requested(symbols, item.path("symbol").asText(q.path("symbol").asText()));
            if (symbol == null) {
                continue;
            }
            found.put(symbol, new PriceQuote(symbol, decimal(q, "price"), decimal(q, "change"),
                    decimal(q, "changePercent"), decimal(q, "previousClose"), decimal(q, "bid"), decimal(q, "ask"),
                    q.path("currency").asText("INR"), instant(q, "asOf"), item.path("stale").asBoolean(false)));
        }
        return found;
    }

    /** Daily candles from one date to another, inclusive, oldest first. None for a symbol it does not know. */
    public List<Candle> candles(String symbol, LocalDate from, LocalDate to) {
        JsonNode body = get("/candles/{symbol}?from={from}&to={to}", symbol, from, to);
        List<Candle> candles = new ArrayList<>();
        if (body == null) {
            return candles;
        }
        for (JsonNode c : body.path("data").path("candles")) {
            candles.add(new Candle(LocalDate.parse(c.path("date").asText()), decimal(c, "open"), decimal(c, "high"),
                    decimal(c, "low"), decimal(c, "close"), c.hasNonNull("volume") ? c.path("volume").asLong() : null));
        }
        return candles;
    }

    /** The body of a 200; null for a 404. Anything else, or no answer, is pricing unavailable. */
    private JsonNode get(String template, Object... variables) {
        try {
            return http.get().uri(template, variables).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (status == 200) {
                    return response.bodyTo(JsonNode.class);
                }
                if (status == 404) {
                    return null;
                }
                if (status == 202) {
                    throw new PricingUnavailableException("Fauxnance is still backfilling " + request.getURI().getPath());
                }
                throw new PricingUnavailableException("Fauxnance answered " + status);
            });
        } catch (ResourceAccessException e) {
            throw new PricingUnavailableException("Fauxnance unreachable", e);
        }
    }

    /** Fauxnance answers with its canonical spelling; key the result by what was asked for. */
    private static String requested(List<String> asked, String answered) {
        return asked.stream().filter(s -> s.equalsIgnoreCase(answered)).findFirst().orElse(null);
    }

    /**
     * A price in paise. Fauxnance's numbers carry float noise (1020.7999877929688)
     * and synthetic ones run to seven places; an order price has two.
     */
    static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue().setScale(2, RoundingMode.HALF_UP) : null;
    }

    private static Instant instant(JsonNode node, String field) {
        return node.hasNonNull(field) ? Instant.parse(node.path(field).asText()) : null;
    }
}
