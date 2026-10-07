package com.yellow.trade.notifications;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class NotificationsKafkaConfigTest {

    @Test
    @DisplayName("its own group, offsets committed by the container after the ledger write, a new group from now on")
    void consumer() {
        Map<String, Object> props = NotificationsKafkaConfig.consumerProperties("localhost:9092", "notification-service");

        assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG), is("notification-service"));
        assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG), is(false));
        assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), is("latest"));
    }

    @Test
    @DisplayName("the offset moves one record at a time, once the listener has returned")
    void ackMode() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new NotificationsKafkaConfig().notificationListenerContainerFactory("localhost:9092", "notification-service");

        assertThat(factory.getContainerProperties().getAckMode(), is(ContainerProperties.AckMode.RECORD));
    }
}
