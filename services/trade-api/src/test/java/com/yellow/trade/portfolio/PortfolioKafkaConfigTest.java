package com.yellow.trade.portfolio;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PortfolioKafkaConfigTest {

    @Test
    @DisplayName("its own group; a new group books the sales the topic still keeps; committed after each booking")
    void consumer() {
        Map<String, Object> props = PortfolioKafkaConfig.consumerProperties("localhost:9092", "portfolio-service");

        assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG), is("portfolio-service"));
        assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), is("earliest"));
        assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG), is(false));
    }
}
