package com.yellow.trade.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Against a real HTTP server standing in for the order route, so the token and the body go over the wire. */
class HttpOrderPlacerTest {

    private static final UUID ORDER = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");

    private HttpServer server;
    private final AtomicReference<String> seenAuthorization = new AtomicReference<>();
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private volatile int status = 201;
    private volatile String reply = "{\"orderId\":\"ORD-" + ORDER + "\",\"status\":\"NEW\"}";
    private final StrategyTokenClient tokens = mock(StrategyTokenClient.class);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/orders", exchange -> {
            seenAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
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

    private HttpOrderPlacer placer() {
        MockEnvironment environment = new MockEnvironment().withProperty("local.server.port",
                String.valueOf(server.getAddress().getPort()));
        return new HttpOrderPlacer(tokens, environment, new ObjectMapper(), RestClient.builder());
    }

    @Test
    @DisplayName("places through the order route with the minted token, a limit price and the firing's idempotency key")
    void places() {
        when(tokens.mint(3L)).thenReturn("a.strategy.token");

        OrderPlacer.Result result = placer().place(3L, "ITC.NS", "BUY", 2, new BigDecimal("260.85"), "strategy-5-abc");

        assertThat(result.placed(), is(true));
        assertThat(result.orderId(), is(ORDER));
        assertThat(seenAuthorization.get(), is("Bearer a.strategy.token"));
        assertThat(seenBody.get(), containsString("\"idempotencyKey\":\"strategy-5-abc\""));
        assertThat(seenBody.get(), containsString("\"price\":260.85"));
        assertThat(seenBody.get(), containsString("\"accountId\":3"));
    }

    @Test
    @DisplayName("the route refusing comes back as its own code and message, as the failure's reason")
    void refused() {
        when(tokens.mint(3L)).thenReturn("a.strategy.token");
        status = 400;
        reply = "{\"errorCode\":\"ORD-400\",\"message\":\"Insufficient funds\"}";

        OrderPlacer.Result result = placer().place(3L, "ITC.NS", "BUY", 2, BigDecimal.TEN, "strategy-5-abc");

        assertThat(result.placed(), is(false));
        assertThat(result.reason(), is("ORD-400: Insufficient funds"));
    }

    @Test
    @DisplayName("no token from auth is a failure, and the route is never called")
    void noToken() {
        when(tokens.mint(3L)).thenThrow(new IllegalStateException("auth would not mint a strategy token: ResourceAccessException"));

        OrderPlacer.Result result = placer().place(3L, "ITC.NS", "BUY", 2, BigDecimal.TEN, "strategy-5-abc");

        assertThat(result.placed(), is(false));
        assertThat(result.reason(), containsString("auth would not mint"));
        assertThat(seenBody.get() == null, is(true));
    }

    @Test
    @DisplayName("the route unreachable is a failure that says so")
    void unreachable() {
        when(tokens.mint(3L)).thenReturn("a.strategy.token");
        server.stop(0);

        OrderPlacer.Result result = placer().place(3L, "ITC.NS", "BUY", 2, BigDecimal.TEN, "strategy-5-abc");

        assertThat(result.reason(), is("the order route could not be reached"));
    }
}
