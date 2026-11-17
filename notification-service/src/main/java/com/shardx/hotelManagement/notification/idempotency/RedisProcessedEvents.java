package com.shardx.hotelManagement.notification.idempotency;

import com.shardx.hotelManagement.notification.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps handled event ids in Redis for a while ({@code notif:evt:<eventId>}).
 *
 * <p><b>Fails open.</b> Redis is an optimisation here, never the source of truth. If it is down, an event is treated as
 * not seen, so a duplicate delivery can send a second e-mail, but no e-mail is ever lost or blocked. (The opposite
 * choice, "when unsure, do not send", would lose e-mails whenever Redis is down.)
 */
@Component
public class RedisProcessedEvents implements ProcessedEvents {

    private static final Logger log = LoggerFactory.getLogger(RedisProcessedEvents.class);
    private static final String KEY_PREFIX = "notif:evt:";

    private final StringRedisTemplate redis;
    private final NotificationProperties properties;

    public RedisProcessedEvents(StringRedisTemplate redis, NotificationProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public boolean wasHandled(String eventId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + eventId));
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, treating event {} as not handled yet (a duplicate e-mail is possible): {}",
                    eventId, e.getMessage());
            return false;
        }
    }

    @Override
    public void markHandled(String eventId) {
        try {
            redis.opsForValue().set(KEY_PREFIX + eventId, "1", properties.dedupTtl());
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, could not remember event {} (a redelivery may send a duplicate e-mail): {}",
                    eventId, e.getMessage());
        }
    }
}
