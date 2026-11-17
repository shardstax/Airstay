package com.shardx.hotelManagement.notification.event;

import java.util.Set;

/** The event types this service sends e-mail for. Any other type is ignored (forward compatible). */
public final class EventTypes {

    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";
    public static final String BOOKING_EXPIRED = "BOOKING_EXPIRED";
    public static final String REFUND_COMPLETED = "REFUND_COMPLETED";

    private static final Set<String> NOTIFIABLE =
            Set.of(BOOKING_CONFIRMED, BOOKING_CANCELLED, BOOKING_EXPIRED, REFUND_COMPLETED);

    private EventTypes() {
    }

    public static boolean isNotifiable(String type) {
        return type != null && NOTIFIABLE.contains(type);
    }
}
