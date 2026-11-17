package com.shardx.hotelManagement.airBnb.places;

/** The place provider failed or could not be reached; the caller can try again later. */
public class PlaceLookupException extends RuntimeException {
    public PlaceLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
