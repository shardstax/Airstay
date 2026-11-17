package com.shardx.hotelManagement.airBnb.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics are created at startup if Kafka is reachable (and by the broker on first use otherwise). Three partitions
 * allow three consumers per group; messages are keyed by booking id, so one booking always lands in the same partition
 * and its events stay in order.
 */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic bookingEventsTopic() {
        return TopicBuilder.name(Topics.BOOKING_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic refundRequestsTopic() {
        return TopicBuilder.name(Topics.REFUND_REQUESTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic refundRequestsDeadLetterTopic() {
        // Same partition count as the topic it belongs to, as the dead-letter recoverer expects by default.
        return TopicBuilder.name(Topics.REFUND_REQUESTS_DLT).partitions(3).replicas(1).build();
    }
}
