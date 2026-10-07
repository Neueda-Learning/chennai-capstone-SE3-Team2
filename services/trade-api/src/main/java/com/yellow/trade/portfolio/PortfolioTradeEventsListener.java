package com.yellow.trade.portfolio;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes trade-events in group portfolio-service, for one thing: to book
 * realised profit and loss at each sale. Read as text, so a message that
 * fails reaches trade-events.DLT exactly as it arrived.
 */
@Component
class PortfolioTradeEventsListener {

    private final RealisedBook book;

    PortfolioTradeEventsListener(RealisedBook book) {
        this.book = book;
    }

    @KafkaListener(
            id = "portfolio-service",
            topics = "${portfolio.topic:trade-events}",
            groupId = "${portfolio.consumer-group:portfolio-service}",
            containerFactory = "portfolioListenerContainerFactory",
            autoStartup = "${portfolio.consumer.auto-startup:true}")
    void onTradeEvent(String value) {
        book.book(value);
    }
}
