package com.shardx.hotelManagement.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code airstay.notifications}. The retry settings are read directly by the listener's
 * {@code @RetryableTopic} (annotations take placeholders); they are listed in application.properties.
 *
 * @param from      the sender shown on every e-mail
 * @param dedupTtl  how long a handled event id is remembered
 */
@ConfigurationProperties(prefix = "airstay.notifications")
public record NotificationProperties(String from, Duration dedupTtl) {
}
