package com.yellow.trade.watchlists;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.trade.watchlists.MarketQuote.UnreadableQuoteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketQuoteTest {

    private final ObjectMapper json = new ObjectMapper();

    /** The QUOTE example in contracts/kafka-topics.md, as the executor's poller publishes it. */
    private static final String QUOTE = """
            {"eventId":"3a5c7e91-2b4d-4f60-8c1e-9d0f2a4b6c8e","eventType":"QUOTE","eventTime":"2026-09-28T09:15:00Z",
             "source":"market-poller","schemaVersion":1,
             "payload":{"symbol":"ITC.NS","price":266.70,"bid":266.65,"ask":266.75,"spreadBps":3.7,"currency":"INR",
                        "change":-2.2,"changePercent":-0.82,"previousClose":268.9,"marketState":"open","stale":false,
                        "quoteAsOf":"2026-09-28T09:14:58Z"}}""";

    @Test
    @DisplayName("reads the contract's quote: the symbol, the price, the observation time, the eventId")
    void reads() {
        MarketQuote quote = MarketQuote.parse(QUOTE, json);

        assertThat(quote.eventId(), is(UUID.fromString("3a5c7e91-2b4d-4f60-8c1e-9d0f2a4b6c8e")));
        assertThat(quote.symbol(), is("ITC.NS"));
        assertThat(quote.price(), comparesEqualTo(new BigDecimal("266.70")));
        assertThat(quote.changePercent(), is(new BigDecimal("-0.82")));
        assertThat(quote.stale(), is(false));
        assertThat(quote.quoteAsOf(), is(Instant.parse("2026-09-28T09:14:58Z")));
    }

    @Test
    @DisplayName("no day change is fine: a null changePercent stays null")
    void noChange() {
        MarketQuote quote = MarketQuote.parse(QUOTE.replace("\"changePercent\":-0.82", "\"changePercent\":null"), json);

        assertThat(quote.changePercent(), is(nullValue()));
    }

    @Test
    @DisplayName("what can never be a quote is refused, to dead-letter: not JSON, not a QUOTE, no price, no time")
    void refused() {
        for (String broken : new String[] {
                "not json",
                QUOTE.replace("\"QUOTE\"", "\"ORDER_FILLED\""),
                QUOTE.replace("\"price\":266.70,", ""),
                QUOTE.replace("\"price\":266.70", "\"price\":0"),
                QUOTE.replace("\"quoteAsOf\":\"2026-09-28T09:14:58Z\"", "\"quoteAsOf\":\"yesterday\""),
                QUOTE.replace("\"symbol\":\"ITC.NS\",", ""),
                QUOTE.replace("\"eventId\":\"3a5c7e91-2b4d-4f60-8c1e-9d0f2a4b6c8e\",", "")}) {
            assertThrows(UnreadableQuoteException.class, () -> MarketQuote.parse(broken, json), broken);
        }
    }
}
