package com.shardx.hotelManagement.airBnb.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.service.BookingExpiryScheduler;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unpaid holds are released by the expiry job, so abandoned checkouts never lock rooms forever. */
class BookingExpiryIT extends IntegrationTestBase {

    @Autowired
    private BookingExpiryScheduler expiryScheduler;

    private final LocalDate checkIn = LocalDate.now().plusDays(50);
    private final LocalDate checkOut = checkIn.plusDays(2);

    @Test
    void lapsedHoldIsReleasedAndTheRoomCanBeBookedAgain() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");
        String guest = newGuestToken();
        long bookingId = data(initBooking(guest, hotel, checkIn, checkOut, 1)).path("id").asLong();
        assertThat(reservedCount(hotel.roomId(), checkIn)).isEqualTo(1);

        // The guest walked away: move the hold's deadline into the past and run the job.
        jdbc.update("UPDATE booking SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), bookingId);
        expiryScheduler.releaseExpiredHolds();

        assertThat(bookingStatus(guest, bookingId)).isEqualTo("EXPIRED");
        assertThat(reservedCount(hotel.roomId(), checkIn)).isZero();
        assertThat(reservedCount(hotel.roomId(), checkIn.plusDays(1))).isZero();

        // The expired booking cannot continue...
        ResponseEntity<JsonNode> addGuests = post("/bookings/" + bookingId + "/addGuests", guest, List.of(addGuest(guest)));
        assertThat(addGuests.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // ...and the freed room is bookable by someone else.
        ResponseEntity<JsonNode> rebook = initBooking(newGuestToken(), hotel, checkIn, checkOut, 1);
        assertThat(rebook.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void holdThatHasNotLapsedIsLeftAlone() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");
        String guest = newGuestToken();
        long bookingId = data(initBooking(guest, hotel, checkIn, checkOut, 1)).path("id").asLong();

        expiryScheduler.releaseExpiredHolds();

        assertThat(bookingStatus(guest, bookingId)).isEqualTo("RESERVED");
        assertThat(reservedCount(hotel.roomId(), checkIn)).isEqualTo(1);
    }
}
