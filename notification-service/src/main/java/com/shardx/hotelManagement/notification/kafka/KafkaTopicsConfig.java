package com.shardx.hotelManagement.notification.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * This service can start before AirStay does, so it makes sure the topic it reads exists. The settings match the
 * ones AirStay uses, so whichever service starts first creates the same topic (creating an existing one is a no-op).
 * The retry topics and the dead-letter topic are declared by {@code @RetryableTopic} on the listener.
 */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic bookingEventsTopic() {
        return TopicBuilder.name(Topics.BOOKING_EVENTS).partitions(3).replicas(1).build();
    }
}
