package com.yellow.trade.strategy;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class StrategyKafkaConfigTest {

    @Test
    @DisplayName("its own group; a new group starts from now: yesterday's quote must never spend today's money")
    void consumer() {
        Map<String, Object> props = StrategyKafkaConfig.consumerProperties("localhost:9092", "strategy-service");

        assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG), is("strategy-service"));
        assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), is("latest"));
        assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG), is(false));
    }
}
