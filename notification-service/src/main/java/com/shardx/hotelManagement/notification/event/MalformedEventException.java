package com.shardx.hotelManagement.notification.event;

/**
 * The message cannot be turned into an e-mail however often it is retried (not JSON, or fields missing). Such a message
 * goes straight to the dead-letter topic instead of through the retry topics.
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message) {
        super(message);
    }

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
