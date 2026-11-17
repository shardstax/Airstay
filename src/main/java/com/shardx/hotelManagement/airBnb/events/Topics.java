package com.shardx.hotelManagement.airBnb.events;

/** Kafka topic names. */
public final class Topics {

    /** Facts about bookings (confirmed, cancelled, expired, refund completed) for any interested service. */
    public static final String BOOKING_EVENTS = "airstay.booking-events";

    /** Work for AirStay's own refund worker: "refund this booking's payment". */
    public static final String REFUND_REQUESTS = "airstay.refund-requests";

    /** Refund requests that still failed after every retry; a human has to look at these. */
    public static final String REFUND_REQUESTS_DLT = REFUND_REQUESTS + ".DLT";

    private Topics() {
    }
}
