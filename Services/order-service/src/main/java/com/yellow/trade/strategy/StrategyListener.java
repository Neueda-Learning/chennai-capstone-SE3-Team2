package com.yellow.trade.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** market-data fires strategies; trade-events says what came of their orders. One group, strategy-service. */
@Component
class StrategyListener {

    private final StrategyTrigger trigger;
    private final StrategyOutcomes outcomes;
    private final ObjectMapper json;
    private final String quotesTopic;

    StrategyListener(StrategyTrigger trigger, StrategyOutcomes outcomes, ObjectMapper json,
                     @Value("${strategy.quotes-topic:market-data}") String quotesTopic) {
        this.trigger = trigger;
        this.outcomes = outcomes;
        this.json = json;
        this.quotesTopic = quotesTopic;
    }

    @KafkaListener(
            id = "strategy-service",
            topics = {"${strategy.quotes-topic:market-data}", "${strategy.outcomes-topic:trade-events}"},
            groupId = "${strategy.consumer-group:strategy-service}",
            containerFactory = "strategyListenerContainerFactory",
            autoStartup = "${strategy.consumer.auto-startup:true}")
    void on(String value, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        if (quotesTopic.equals(topic)) {
            trigger.onQuote(StrategyQuote.parse(value, json));
        } else {
            outcomes.apply(value);
        }
    }
}
