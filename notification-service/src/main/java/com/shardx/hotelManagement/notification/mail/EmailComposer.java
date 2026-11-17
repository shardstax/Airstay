package com.shardx.hotelManagement.notification.mail;

import com.shardx.hotelManagement.notification.event.BookingEvent;
import com.shardx.hotelManagement.notification.event.EventTypes;
import com.shardx.hotelManagement.notification.event.MalformedEventException;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Writes the e-mail for an event using ONLY what is in the event (it never calls back into AirStay).
 *
 * <p>Dates: the event's {@code checkOutDate} is the departure day, not a night of the stay, so the number of nights is
 * check-out minus check-in. Amounts are shown in INR because the contract has no currency field yet.
 */
@Component
public class EmailComposer {

    /** The e-mail for this event, or empty if the type is not one we send e-mail for (it is then ignored). */
    public Optional<EmailMessage> compose(BookingEvent event) {
        if (!EventTypes.isNotifiable(event.type())) {
            return Optional.empty();
        }
        requireFieldsForEmail(event);

        String subject;
        String message;
        switch (event.type()) {
            case EventTypes.BOOKING_CONFIRMED -> {
                subject = "Your AirStay booking #" + event.bookingId() + " is confirmed";
                message = "Your booking is confirmed. We look forward to hosting you.";
            }
            case EventTypes.BOOKING_CANCELLED -> {
                subject = "Your AirStay booking #" + event.bookingId() + " was cancelled";
                message = "Your booking was cancelled. A refund of " + money(event) + " has been requested. "
                        + "We will e-mail you again when it has been issued.";
            }
            case EventTypes.BOOKING_EXPIRED -> {
                subject = "Your AirStay hold for booking #" + event.bookingId() + " expired";
                message = "The payment was not completed in time, so the rooms were released and are no longer "
                        + "held for you. If you were charged anything, it will be refunded automatically.";
            }
            case EventTypes.REFUND_COMPLETED -> {
                subject = "Your refund for AirStay booking #" + event.bookingId() + " has been issued";
                message = "Your refund of " + money(event) + " has been issued. "
                        + "Your bank may take a few days to show it on your statement.";
            }
            default -> throw new IllegalStateException("No template for " + event.type());
        }

        return Optional.of(new EmailMessage(event.userEmail().trim(), subject, body(event, message)));
    }

    private static String body(BookingEvent event, String message) {
        long nights = ChronoUnit.DAYS.between(event.checkInDate(), event.checkOutDate());
        return "Hi " + greetingName(event) + ",\n\n"
                + message + "\n\n"
                + "Booking:    #" + event.bookingId() + "\n"
                + "Hotel:      " + event.hotelName() + "\n"
                + "Room:       " + event.roomType() + " x " + event.roomsCount() + "\n"
                + "Check-in:   " + event.checkInDate() + "\n"
                + "Check-out:  " + event.checkOutDate() + " (departure day)\n"
                + "Nights:     " + nights + "\n"
                + "Amount:     " + money(event) + "\n\n"
                + "Thank you for choosing AirStay.\n";
    }

    private static String greetingName(BookingEvent event) {
        return event.userName() == null || event.userName().isBlank() ? "there" : event.userName().trim();
    }

    private static String money(BookingEvent event) {
        return "INR " + event.amount().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Without these there is nothing sensible to write, and retrying cannot add them: the event is malformed. */
    private static void requireFieldsForEmail(BookingEvent event) {
        require(event, event.userEmail() != null && !event.userEmail().isBlank(), "userEmail");
        require(event, event.bookingId() != null, "bookingId");
        require(event, event.hotelName() != null && !event.hotelName().isBlank(), "hotelName");
        require(event, event.roomType() != null && !event.roomType().isBlank(), "roomType");
        require(event, event.checkInDate() != null, "checkInDate");
        require(event, event.checkOutDate() != null, "checkOutDate");
        require(event, event.roomsCount() != null, "roomsCount");
        require(event, event.amount() != null, "amount");
    }

    private static void require(BookingEvent event, boolean present, String field) {
        if (!present) {
            throw new MalformedEventException(
                    "Event " + event.eventId() + " (" + event.type() + ") has no " + field);
        }
    }
}
