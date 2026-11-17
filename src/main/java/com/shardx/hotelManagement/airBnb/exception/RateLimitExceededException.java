package com.shardx.hotelManagement.airBnb.exception;

import lombok.Getter;

/** A client sent too many requests inside the rate-limit window. Becomes HTTP 429 with a Retry-After header. */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too many requests, please retry in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
