package com.yellow.trade.advice;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AdviceKafkaConfigTest {

    @Test
    @DisplayName("its own group; a new group starts from now, since an old price is no use to a signal")
    void consumer() {
        Map<String, Object> props = AdviceKafkaConfig.consumerProperties("localhost:9092", "advice-service");

        assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG), is("advice-service"));
        assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), is("latest"));
    }
}
