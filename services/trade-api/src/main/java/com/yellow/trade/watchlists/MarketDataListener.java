package com.yellow.trade.watchlists;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes market-data in group watchlist-service: every quote the poller
 * publishes passes here once. Read as text and parsed here, so a message
 * that fails reaches market-data.DLT exactly as it arrived.
 */
@Component
class MarketDataListener {

    private final QuoteEvaluator evaluator;
    private final ObjectMapper json;

    MarketDataListener(QuoteEvaluator evaluator, ObjectMapper json) {
        this.evaluator = evaluator;
        this.json = json;
    }

    @KafkaListener(
            id = "watchlist-service",
            topics = "${watchlists.topic:market-data}",
            groupId = "${watchlists.consumer-group:watchlist-service}",
            containerFactory = "watchlistListenerContainerFactory",
            autoStartup = "${watchlists.consumer.auto-startup:true}")
    void onQuote(String value) {
        evaluator.evaluate(MarketQuote.parse(value, json));
    }
}
