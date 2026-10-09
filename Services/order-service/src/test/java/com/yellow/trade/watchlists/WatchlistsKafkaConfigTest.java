package com.yellow.trade.watchlists;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WatchlistsKafkaConfigTest {

    @Test
    @DisplayName("its own group, committed after each quote's transaction, a new group from now on")
    void consumer() {
        Map<String, Object> props = WatchlistsKafkaConfig.consumerProperties("localhost:9092", "watchlist-service");

        assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG), is("watchlist-service"));
        assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG), is(false));
        assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), is("latest"));
        assertThat(new WatchlistsKafkaConfig().watchlistListenerContainerFactory("localhost:9092", "watchlist-service")
                .getContainerProperties().getAckMode(), is(ContainerProperties.AckMode.RECORD));
    }
}
