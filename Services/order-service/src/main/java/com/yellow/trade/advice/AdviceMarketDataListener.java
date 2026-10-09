package com.yellow.trade.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes market-data in group advice-service, for the latest price only.
 * Nothing is recomputed here: the timer does that.
 */
@Component
class AdviceMarketDataListener {

    private final LatestPrices prices;
    private final ObjectMapper json;

    AdviceMarketDataListener(LatestPrices prices, ObjectMapper json) {
        this.prices = prices;
        this.json = json;
    }

    @KafkaListener(
            id = "advice-service",
            topics = "${advice.topic:market-data}",
            groupId = "${advice.consumer-group:advice-service}",
            containerFactory = "adviceListenerContainerFactory",
            autoStartup = "${advice.consumer.auto-startup:true}")
    void onQuote(String value) {
        prices.offer(value, json);
    }
}
