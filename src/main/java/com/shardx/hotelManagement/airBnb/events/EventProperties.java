package com.shardx.hotelManagement.airBnb.events;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings for the outbox relay and the Kafka consumers (prefix {@code airstay.events}). */
@Data
@Component
@ConfigurationProperties(prefix = "airstay.events")
public class EventProperties {

    /** Master switch for the relay. Off = events stay in the outbox table until it is switched on again. */
    private boolean enabled = true;

    /** Events read from the outbox per database transaction. */
    private int batchSize = 100;

    /** How long to wait for Kafka to acknowledge one event before giving up on this round. */
    private long sendTimeoutMs = 5000;

    /** Published outbox rows older than this are deleted. */
    private int retentionDays = 7;
}
