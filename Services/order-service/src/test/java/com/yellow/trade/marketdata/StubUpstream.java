package com.yellow.trade.marketdata;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A one-route HTTP server standing in for Fauxnance or the MF NAV service. */
final class StubUpstream implements AutoCloseable {

    private final HttpServer server;
    /** Path and query as the server reads them, decoded. */
    final List<String> requests = new CopyOnWriteArrayList<>();
    /** Exactly as sent, still encoded. */
    final List<String> rawRequests = new CopyOnWriteArrayList<>();
    final List<String> apiKeys = new CopyOnWriteArrayList<>();
    volatile int status = 200;
    volatile String reply = "{}";

    StubUpstream() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            requests.add(exchange.getRequestURI().getPath() + (query == null ? "" : "?" + query));
            rawRequests.add(exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()));
            apiKeys.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Api-Key")));
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
