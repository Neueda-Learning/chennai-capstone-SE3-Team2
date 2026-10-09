package com.yellow.trade.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LatestPricesTest {

    private static final String QUOTE = """
            {"eventId":"3a5c7e91-2b4d-4f60-8c1e-9d0f2a4b6c8e","eventType":"QUOTE","eventTime":"2026-10-07T04:00:00Z",
             "source":"market-poller","schemaVersion":1,"payload":{"symbol":"ITC.NS","price":266.70,"bid":266.65,
             "ask":266.75,"currency":"INR","change":-2.2,"changePercent":-0.82,"previousClose":268.9,
             "marketState":"open","stale":false,"quoteAsOf":"2026-10-07T03:59:58Z"}}""";

    @Test
    @DisplayName("a quote off market-data becomes the latest price, with when it was observed")
    void reads() {
        LatestPrices prices = new LatestPrices();

        prices.offer(QUOTE, new ObjectMapper());

        assertThat(prices.of("ITC.NS").orElseThrow().price(), comparesEqualTo(new BigDecimal("266.70")));
        assertThat(prices.of("ITC.NS").orElseThrow().asOf(), is(Instant.parse("2026-10-07T03:59:58Z")));
    }

    @Test
    @DisplayName("a quote observed earlier than the one held changes nothing")
    void older() {
        LatestPrices prices = new LatestPrices();
        prices.offer("ITC.NS", new BigDecimal("270"), Instant.parse("2026-10-07T04:05:00Z"));

        prices.offer(QUOTE, new ObjectMapper());

        assertThat(prices.of("ITC.NS").orElseThrow().price(), comparesEqualTo(new BigDecimal("270")));
    }

    @Test
    @DisplayName("what can never be a quote is refused, to dead-letter")
    void unreadable() {
        LatestPrices prices = new LatestPrices();

        assertThrows(AdviceExceptions.UnreadableQuoteException.class, () -> prices.offer("not json", new ObjectMapper()));
        assertThrows(AdviceExceptions.UnreadableQuoteException.class,
                () -> prices.offer(QUOTE.replace("\"price\":266.70,", ""), new ObjectMapper()));
    }
}
