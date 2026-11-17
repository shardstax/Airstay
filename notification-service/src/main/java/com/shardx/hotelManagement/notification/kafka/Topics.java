package com.shardx.hotelManagement.notification.kafka;

/** The topic this service reads (owned and created by AirStay; see docs/events.md). */
public final class Topics {

    /** Facts about bookings: confirmed, cancelled, expired, refund completed. */
    public static final String BOOKING_EVENTS = "airstay.booking-events";

    private Topics() {
    }
}
