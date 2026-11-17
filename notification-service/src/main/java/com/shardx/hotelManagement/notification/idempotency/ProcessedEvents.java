package com.shardx.hotelManagement.notification.idempotency;

/**
 * Remembers which event ids were already e-mailed. Kafka delivery is at-least-once, so the same event can arrive twice
 * (for example after a crash between sending the e-mail and committing the offset).
 */
public interface ProcessedEvents {

    boolean wasHandled(String eventId);

    /** Call only AFTER the e-mail was sent: marking first would turn a failed send into a lost e-mail. */
    void markHandled(String eventId);
}
