package com.shardx.hotelManagement.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * This service's own copy of the event AirStay publishes on {@code airstay.booking-events} (the contract is
 * docs/events.md at the repository root). It is deliberately not shared code: the two services only agree on the JSON.
 *
 * <p>Fields the API adds later are ignored, so the API can evolve without breaking this service ("only ever add fields").
 * Wrapper types (Long, Integer) so that a missing field is {@code null} instead of a misleading 0.
 * {@code paymentSessionId} is not read: the contract says it is only ever set on refund requests, never on this topic.
 *
 * @param checkOutDate the departure day, not a night of the stay
 * @param amount       what the guest paid, in INR (the contract has no currency field yet)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingEvent(
        String eventId,
        String type,
        LocalDateTime occurredAt,
        Long bookingId,
        Long userId,
        String userEmail,
        String userName,
        String hotelName,
        String roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer roomsCount,
        BigDecimal amount) {
}
