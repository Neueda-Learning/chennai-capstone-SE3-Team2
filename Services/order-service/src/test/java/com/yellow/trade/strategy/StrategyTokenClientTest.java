package com.yellow.trade.strategy;

import com.sun.net.httpserver.HttpServer;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Against a real HTTP server standing in for auth's internal route. */
class StrategyTokenClientTest {

    private static final String SECRET = "an-internal-test-secret-of-32-plus-bytes";

    private HttpServer server;
    private final AtomicReference<String> seenSecret = new AtomicReference<>();
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private volatile int status = 201;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/strategy-tokens", exchange -> {
            seenSecret.set(exchange.getRequestHeaders().getFirst("X-Internal-Secret"));
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = (status == 201 ? "{\"accessToken\":\"a.b.c\",\"tokenType\":\"Bearer\",\"expiresIn\":300}"
                    : "{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}").getBytes(StandardCharsets.UTF_8);
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

    private StrategyTokenClient client() {
        return new StrategyTokenClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(), SECRET);
    }

    @Test
    @DisplayName("sends the shared secret and the account, and returns the token")
    void mints() {
        assertThat(client().mint(3), is("a.b.c"));
        assertThat(seenSecret.get(), is(SECRET));
        assertThat(seenBody.get(), is("{\"accountId\":3}"));
    }

    @Test
    @DisplayName("auth refusing is an error that names neither the secret nor a token")
    void refused() {
        status = 401;

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> client().mint(3));

        assertThat(refused.getMessage(), not(containsString(SECRET)));
    }
}
