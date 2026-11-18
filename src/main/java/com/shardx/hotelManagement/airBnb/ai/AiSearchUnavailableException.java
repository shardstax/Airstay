package com.shardx.hotelManagement.airBnb.ai;

/** The optional assistant is not configured; ordinary hotel search remains available. */
public class AiSearchUnavailableException extends RuntimeException {
    public AiSearchUnavailableException(String message) {
        super(message);
    }
}
