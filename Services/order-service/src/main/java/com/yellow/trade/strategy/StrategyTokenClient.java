package com.yellow.trade.strategy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Asks auth, on its internal route behind the service secret, for an access
 * token five minutes long for one account (decision log 0012). The same
 * secret and base URL the activation mailer already holds. The token is a
 * credential: it is used at once and never logged or stored.
 */
@Component
public class StrategyTokenClient {

    static final String SECRET_HEADER = "X-Internal-Secret";

    private final RestClient http;
    private final String secret;

    public StrategyTokenClient(RestClient.Builder builder,
                               @Value("${activation.auth-base-url}") String authBaseUrl,
                               @Value("${activation.internal-secret}") String secret) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(2_000);
        timeouts.setReadTimeout(5_000);
        this.http = builder.baseUrl(authBaseUrl).requestFactory(timeouts).build();
        this.secret = secret;
    }

    /** @throws IllegalStateException when auth will not mint one; the reason names no secret and no token */
    public String mint(long accountId) {
        try {
            TokenResponse response = http.post()
                    .uri("/internal/strategy-tokens")
                    .header(SECRET_HEADER, secret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("accountId", accountId))
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new IllegalStateException("auth answered without a token");
            }
            return response.accessToken();
        } catch (RestClientException e) {
            throw new IllegalStateException("auth would not mint a strategy token: " + e.getClass().getSimpleName());
        }
    }

    record TokenResponse(String accessToken, String tokenType, Integer expiresIn) {
        /** Never print the token, however this record ends up in a log. */
        @Override
        public String toString() {
            return "TokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
        }
    }
}
