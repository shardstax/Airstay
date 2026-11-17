package com.shardx.hotelManagement.notification.mail;

import com.shardx.hotelManagement.notification.event.BookingEvent;
import com.shardx.hotelManagement.notification.event.MalformedEventException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailComposerTest {

    private final EmailComposer composer = new EmailComposer();

    @Test
    void confirmationHasTheStayDetails() {
        EmailMessage email = composer.compose(event("BOOKING_CONFIRMED")).orElseThrow();

        assertThat(email.to()).isEqualTo("asha@example.com");
        assertThat(email.subject()).isEqualTo("Your AirStay booking #99 is confirmed");
        assertThat(email.body())
                .startsWith("Hi Asha,")
                .contains("Your booking is confirmed")
                .contains("Booking:    #99")
                .contains("Hotel:      Sea View Resort")
                .contains("Room:       Deluxe x 2")
                .contains("Check-in:   2026-12-01")
                .contains("Check-out:  2026-12-03 (departure day)")
                .contains("Amount:     INR 4000.00");
    }

    @Test
    void nightsAreCheckOutMinusCheckInBecauseTheDepartureDayIsNotANight() {
        assertThat(composer.compose(event("BOOKING_CONFIRMED")).orElseThrow().body()).contains("Nights:     2");
    }

    @Test
    void cancellationSaysARefundWasRequested() {
        EmailMessage email = composer.compose(event("BOOKING_CANCELLED")).orElseThrow();

        assertThat(email.subject()).isEqualTo("Your AirStay booking #99 was cancelled");
        assertThat(email.body()).contains("A refund of INR 4000.00 has been requested");
    }

    @Test
    void expiredHoldExplainsThatTheRoomsWereReleased() {
        EmailMessage email = composer.compose(event("BOOKING_EXPIRED")).orElseThrow();

        assertThat(email.subject()).isEqualTo("Your AirStay hold for booking #99 expired");
        assertThat(email.body()).contains("the rooms were released");
    }

    @Test
    void refundCompletedSaysTheRefundWasIssued() {
        EmailMessage email = composer.compose(event("REFUND_COMPLETED")).orElseThrow();

        assertThat(email.subject()).isEqualTo("Your refund for AirStay booking #99 has been issued");
        assertThat(email.body()).contains("Your refund of INR 4000.00 has been issued");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOTEL_RENAMED", "REFUND_REQUESTED", "booking_confirmed", ""})
    void otherTypesSendNothing(String type) {
        // REFUND_REQUESTED is work for the refund worker; a guest must never get an e-mail for it.
        assertThat(composer.compose(event(type))).isEmpty();
    }

    @Test
    void usesAFriendlyGreetingWhenThereIsNoName() {
        assertThat(composer.compose(withName(null)).orElseThrow().body()).startsWith("Hi there,");
        assertThat(composer.compose(withName("  ")).orElseThrow().body()).startsWith("Hi there,");
    }

    @Test
    void trimsTheRecipientAddress() {
        BookingEvent padded = new BookingEvent("e1", "BOOKING_CONFIRMED", LocalDateTime.now(), 99L, 7L,
                "  asha@example.com ", "Asha", "Sea View Resort", "Deluxe", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 3), 2, new BigDecimal("4000"));

        assertThat(composer.compose(padded).orElseThrow().to()).isEqualTo("asha@example.com");
    }

    @Test
    void showsMoneyWithTwoDecimals() {
        assertThat(composer.compose(withAmount("1234.5")).orElseThrow().body()).contains("INR 1234.50");
        assertThat(composer.compose(withAmount("99.999")).orElseThrow().body()).contains("INR 100.00");
        assertThat(composer.compose(withAmount("4000")).orElseThrow().body()).contains("INR 4000.00");
    }

    @Test
    void aKnownEventMissingWhatTheEmailNeedsIsMalformedNotRetryable() {
        BookingEvent noEmail = new BookingEvent("e1", "BOOKING_CONFIRMED", LocalDateTime.now(), 99L, 7L,
                " ", "Asha", "Sea View Resort", "Deluxe", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 2,
                new BigDecimal("4000"));
        BookingEvent noHotel = new BookingEvent("e1", "BOOKING_CONFIRMED", LocalDateTime.now(), 99L, 7L,
                "a@b.test", "Asha", null, "Deluxe", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 2,
                new BigDecimal("4000"));
        BookingEvent noCheckOut = new BookingEvent("e1", "BOOKING_CONFIRMED", LocalDateTime.now(), 99L, 7L,
                "a@b.test", "Asha", "H", "Deluxe", LocalDate.of(2026, 12, 1), null, 2, new BigDecimal("4000"));
        BookingEvent noAmount = new BookingEvent("e1", "BOOKING_CONFIRMED", LocalDateTime.now(), 99L, 7L,
                "a@b.test", "Asha", "H", "Deluxe", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 2, null);

        for (BookingEvent broken : new BookingEvent[]{noEmail, noHotel, noCheckOut, noAmount}) {
            assertThatThrownBy(() -> composer.compose(broken)).isInstanceOf(MalformedEventException.class);
        }
    }

    @Test
    void anUnknownTypeIsIgnoredEvenWhenItsFieldsAreMissing() {
        BookingEvent bare = new BookingEvent("e1", "SOMETHING_NEW", null, null, null, null, null, null, null, null,
                null, null, null);

        assertThat(composer.compose(bare)).isEmpty();
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static BookingEvent event(String type) {
        return new BookingEvent("evt-1", type, LocalDateTime.of(2026, 11, 17, 10, 0), 99L, 7L, "asha@example.com",
                "Asha", "Sea View Resort", "Deluxe", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 2,
                new BigDecimal("4000"));
    }

    private static BookingEvent withName(String name) {
        return new BookingEvent("evt-1", "BOOKING_CONFIRMED", LocalDateTime.now(), 99L, 7L, "asha@example.com", name,
                "Sea View Resort", "Deluxe", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 2,
                new BigDecimal("4000"));
    }

    private static BookingEvent withAmount(String amount) {
        return new BookingEvent("evt-1", "BOOKING_CONFIRMED", LocalDateTime.now(), 99L, 7L, "asha@example.com", "Asha",
                "Sea View Resort", "Deluxe", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 2,
                new BigDecimal(amount));
    }
}
