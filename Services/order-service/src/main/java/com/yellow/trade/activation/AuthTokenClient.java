package com.yellow.trade.activation;

import com.yellow.trade.activation.ActivationExceptions.AccountAlreadyClaimedException;
import com.yellow.trade.activation.ActivationExceptions.AuthRefusedException;
import com.yellow.trade.activation.ActivationExceptions.AuthUnavailableException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Asks auth for a one-time activation token over the internal network.
 *
 * The token is a credential, so it never touches Kafka: this HTTP response and
 * the email are the only two places its plaintext ever exists. Auth keeps only
 * its hash. Nothing here logs the token.
 */
@Component
public class AuthTokenClient {

    static final String SECRET_HEADER = "X-Internal-Secret";

    private final RestClient http;
    private final String secret;

    public AuthTokenClient(RestClient.Builder builder, ActivationProperties properties) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        // Bounded, so an unresponsive auth becomes a retry rather than a
        // consumer thread parked past max.poll.interval.ms.
        timeouts.setConnectTimeout(2_000);
        timeouts.setReadTimeout(5_000);
        this.http = builder.baseUrl(properties.authBaseUrl()).requestFactory(timeouts).build();
        this.secret = properties.internalSecret();
    }

    public String mintToken(long clientId) {
        try {
            TokenResponse response = http.post()
                    .uri("/internal/activation-tokens")
                    .header(SECRET_HEADER, secret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("clientId", clientId))
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || response.activationToken() == null || response.activationToken().isBlank()) {
                throw new AuthRefusedException("auth answered without a token for client " + clientId);
            }
            return response.activationToken();

        } catch (ResourceAccessException e) {
            // Connection refused, DNS, timeout: auth is down or restarting.
            throw new AuthUnavailableException("auth unreachable minting a token for client " + clientId, e);
        } catch (RestClientResponseException e) {
            throw classify(e.getStatusCode(), clientId, e);
        }
    }

    private static RuntimeException classify(HttpStatusCode status, long clientId, RestClientResponseException e) {
        if (status.is5xxServerError()) {
            return new AuthUnavailableException("auth answered " + status.value() + " for client " + clientId, e);
        }
        if (status.value() == 409) {
            return new AccountAlreadyClaimedException(clientId);
        }
        if (status.value() == 404) {
            return new AuthRefusedException("auth has no provisioned account " + clientId);
        }
        if (status.value() == 401) {
            // The shared secret is wrong or missing. Retrying will not fix configuration.
            return new AuthRefusedException("auth refused the internal secret");
        }
        return new AuthRefusedException("auth answered " + status.value() + " for client " + clientId);
    }

    record TokenResponse(String activationToken, String expiresAt) {
        /** Never print the token, however this record ends up in a log. */
        @Override
        public String toString() {
            return "TokenResponse[expiresAt=" + expiresAt + "]";
        }
    }
}
