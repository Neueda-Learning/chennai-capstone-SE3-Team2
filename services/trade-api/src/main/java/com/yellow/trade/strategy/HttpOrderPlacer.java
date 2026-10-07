package com.yellow.trade.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * POST /api/v1/orders on this same service, over HTTP, with a token auth
 * minted for the strategy's account: the order passes the token filter, the
 * validation, the authorisation and the idempotency check, as a customer's
 * does. The idempotency key is the strategy and the quote, so a retried or
 * replayed firing is one order.
 */
@Component
class HttpOrderPlacer implements OrderPlacer {

    private static final String DISPLAY_PREFIX = "ORD-";

    private final StrategyTokenClient tokens;
    private final Environment environment;
    private final ObjectMapper json;
    private final RestClient http;

    HttpOrderPlacer(StrategyTokenClient tokens, Environment environment, ObjectMapper json, RestClient.Builder builder) {
        this.tokens = tokens;
        this.environment = environment;
        this.json = json;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(2_000);
        timeouts.setReadTimeout(10_000);
        this.http = builder.requestFactory(timeouts).build();
    }

    @Override
    public Result place(long accountId, String symbol, String side, int quantity, BigDecimal limitPrice,
                        String idempotencyKey) {
        String token;
        try {
            token = tokens.mint(accountId);
        } catch (IllegalStateException e) {
            return Result.failed(e.getMessage());
        }
        try {
            String body = http.post()
                    .uri(ordersUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("accountId", accountId, "symbol", symbol, "side", side, "quantity", quantity,
                            "price", limitPrice, "idempotencyKey", idempotencyKey))
                    .retrieve()
                    .body(String.class);
            String displayed = json.readTree(body).path("orderId").asText("");
            return Result.placed(UUID.fromString(displayed.startsWith(DISPLAY_PREFIX)
                    ? displayed.substring(DISPLAY_PREFIX.length()) : displayed));
        } catch (RestClientResponseException e) {
            return Result.failed(envelope(e.getResponseBodyAsString(), e.getStatusCode().value()));
        } catch (RestClientException | IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return Result.failed("the order route could not be reached");
        }
    }

    /** This service's own port: the route is the same one a customer's browser calls. */
    String ordersUrl() {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8085"));
        return "http://localhost:" + port + "/api/v1/orders";
    }

    /** The route's own words: its error code and message, never a token. */
    private String envelope(String body, int status) {
        try {
            JsonNode error = json.readTree(body);
            return error.path("errorCode").asText("HTTP " + status) + ": " + error.path("message").asText("refused");
        } catch (Exception e) {
            return "the order route answered " + status;
        }
    }
}
