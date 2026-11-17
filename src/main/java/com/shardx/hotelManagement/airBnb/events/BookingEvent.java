package com.shardx.hotelManagement.airBnb.events;

import com.shardx.hotelManagement.airBnb.entity.Booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The JSON message published to Kafka: a self-contained snapshot of the booking at that moment, so consumers (for
 * example the notification service) never have to call back into AirStay to find out who, where or how much.
 * This is a public contract, see docs/events.md: only ever add fields, never rename or remove them.
 *
 * @param eventId          unique per event; consumers use it to ignore duplicates (delivery is at-least-once)
 * @param paymentSessionId only set on REFUND_REQUESTED (it is the refund worker's input, other consumers never get it)
 */
public record BookingEvent(
        String eventId,
        String type,
        LocalDateTime occurredAt,
        long bookingId,
        long userId,
        String userEmail,
        String userName,
        String hotelName,
        String roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int roomsCount,
        BigDecimal amount,
        String paymentSessionId) {

    /** Must run inside the booking's transaction: it reads lazily loaded user, hotel and room. */
    public static BookingEvent of(BookingEventType type, Booking booking, LocalDateTime now) {
        return of(type, booking, now, UUID.randomUUID());
    }

    /** Same, with a chosen event id: lets an event be derived from its cause so it can never be recorded twice. */
    public static BookingEvent of(BookingEventType type, Booking booking, LocalDateTime now, UUID eventId) {
        return new BookingEvent(
                eventId.toString(),
                type.name(),
                now,
                booking.getId(),
                booking.getUser().getId(),
                booking.getUser().getEmail(),
                booking.getUser().getName(),
                booking.getHotel().getName(),
                booking.getRoom().getType(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getRoomsCount(),
                booking.getAmount(),
                type == BookingEventType.REFUND_REQUESTED ? booking.getPaymentSessionId() : null);
    }
}
