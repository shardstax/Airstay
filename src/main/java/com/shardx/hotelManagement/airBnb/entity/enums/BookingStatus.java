package com.shardx.hotelManagement.airBnb.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * RESERVED -> GUESTS_ADDED -> PAYMENTS_PENDING -> CONFIRMED -> CANCELLED
 * Any of the first three can lapse to EXPIRED, which gives the held rooms back.
 */
public enum BookingStatus {
    RESERVED,
    GUESTS_ADDED,
    PAYMENTS_PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    /** Statuses in which the booking holds rooms in inventory.reservedCount. */
    public static final Set<BookingStatus> HOLD_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(RESERVED, GUESTS_ADDED, PAYMENTS_PENDING));

    public boolean isHold() {
        return HOLD_STATUSES.contains(this);
    }
}
