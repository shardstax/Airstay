package com.shardx.hotelManagement.airBnb.events;

public enum BookingEventType {
    /** Payment succeeded; the rooms are booked. */
    BOOKING_CONFIRMED(Topics.BOOKING_EVENTS),
    /** A confirmed booking was cancelled by the guest. */
    BOOKING_CANCELLED(Topics.BOOKING_EVENTS),
    /** An unpaid hold lapsed or its payment session expired; the rooms were released. */
    BOOKING_EXPIRED(Topics.BOOKING_EVENTS),
    /** Command for the refund worker: give this booking's payment back. */
    REFUND_REQUESTED(Topics.REFUND_REQUESTS),
    /** The refund was issued. */
    REFUND_COMPLETED(Topics.BOOKING_EVENTS);

    private final String topic;

    BookingEventType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}
