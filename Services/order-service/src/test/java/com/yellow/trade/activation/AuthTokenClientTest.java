package com.yellow.trade.activation;

import com.sun.net.httpserver.HttpServer;
import com.yellow.trade.activation.ActivationExceptions.AccountAlreadyClaimedException;
import com.yellow.trade.activation.ActivationExceptions.AuthRefusedException;
import com.yellow.trade.activation.ActivationExceptions.AuthUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Against a real HTTP server standing in for auth, so headers and bodies go over the wire. */
class AuthTokenClientTest {

    private static final String SECRET = "an-internal-test-secret-of-32-plus-bytes";
    private static final String TOKEN = "ab".repeat(32);

    private HttpServer server;
    private final AtomicReference<String> seenSecret = new AtomicReference<>();
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private volatile int status = 201;
    private volatile String reply = "{\"activationToken\":\"" + TOKEN + "\",\"expiresAt\":\"2026-10-01T18:22:41.000Z\"}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/activation-tokens", exchange -> {
            seenSecret.set(exchange.getRequestHeaders().getFirst("X-Internal-Secret"));
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AuthTokenClient client(String baseUrl) {
        return new AuthTokenClient(RestClient.builder(), new ActivationProperties(
                "account-provisioning", "activation-mailer", baseUrl, SECRET,
                "http://localhost:3000/activate", "noreply@example.com"));
    }

    private AuthTokenClient client() {
        return client("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    @DisplayName("sends the shared secret and the client id, and returns the token")
    void mintsAToken() {
        assertThat(client().mintToken(7)).isEqualTo(TOKEN);

        assertThat(seenSecret.get()).isEqualTo(SECRET);
        assertThat(seenBody.get()).isEqualTo("{\"clientId\":7}");
    }

    @Test
    @DisplayName("auth answering 5xx is transient")
    void serverErrorIsTransient() {
        status = 503;
        reply = "{}";
        assertThatThrownBy(() -> client().mintToken(7)).isInstanceOf(AuthUnavailableException.class);
    }

    @Test
    @DisplayName("auth not listening at all is transient")
    void unreachableIsTransient() {
        int port = server.getAddress().getPort();
        server.stop(0);
        assertThatThrownBy(() -> client("http://127.0.0.1:" + port).mintToken(7))
                .isInstanceOf(AuthUnavailableException.class);
    }

    @Test
    @DisplayName("an account that already has a login is reported, not retried")
    void conflictIsAlreadyClaimed() {
        status = 409;
        reply = "{\"errorCode\":\"ACT-409\",\"message\":\"Account already has a login\"}";
        assertThatThrownBy(() -> client().mintToken(7)).isInstanceOf(AccountAlreadyClaimedException.class);
    }

    @Test
    @DisplayName("an unprovisioned account and a refused secret are poison: no retry will fix either")
    void refusalsArePoison() {
        status = 404;
        reply = "{\"errorCode\":\"ACT-404\",\"message\":\"Account not provisioned\"}";
        assertThatThrownBy(() -> client().mintToken(7)).isInstanceOf(AuthRefusedException.class);

        status = 401;
        reply = "{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}";
        assertThatThrownBy(() -> client().mintToken(7)).isInstanceOf(AuthRefusedException.class);
    }
}
