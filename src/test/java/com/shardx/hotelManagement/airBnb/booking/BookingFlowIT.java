package com.shardx.hotelManagement.airBnb.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.service.CheckoutService;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The full guest journey over HTTP, checking inventory counts in the database after every step. */
class BookingFlowIT extends IntegrationTestBase {

    private final LocalDate checkIn = LocalDate.now().plusDays(20);
    private final LocalDate lastNight = checkIn.plusDays(1);
    private final LocalDate checkOut = checkIn.plusDays(2); // 2-night stay

    @Test
    void guestSearchesBooksPaysAndCancelsWithInventoryAlwaysConsistent() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 2, "1000.00");

        // Search finds the newly activated hotel at its nightly price
        ResponseEntity<JsonNode> search = searchHotels(hotel.city(), 1);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode results = data(search).path("content");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).path("id").asLong()).isEqualTo(hotel.hotelId());
        assertThat(results.get(0).path("price").asDouble()).isEqualTo(1000.0);
        // The paging block is part of the public contract (PagedModel shape, not Spring's internal Page JSON)
        JsonNode page = data(search).path("page");
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("totalPages").asInt()).isEqualTo(1);
        assertThat(page.path("number").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(10);
        assertThat(data(search).has("pageable")).as("Spring's internal Page fields must not leak").isFalse();

        // Hold: both nights are reserved, the departure day is not
        String guest = newGuestToken();
        long guestId = addGuest(guest);
        ResponseEntity<JsonNode> init = initBooking(guest, hotel, checkIn, checkOut, 1);
        assertThat(init.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long bookingId = data(init).path("id").asLong();
        assertThat(data(init).path("amount").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(data(init).path("bookingStatus").asText()).isEqualTo("RESERVED");
        assertThat(reservedCount(hotel.roomId(), checkIn)).isEqualTo(1);
        assertThat(reservedCount(hotel.roomId(), lastNight)).isEqualTo(1);
        assertThat(reservedCount(hotel.roomId(), checkOut)).isZero();

        // Guests, then payment
        ResponseEntity<JsonNode> guests = post("/bookings/" + bookingId + "/addGuests", guest, List.of(guestId));
        assertThat(guests.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(guests).path("bookingStatus").asText()).isEqualTo("GUESTS_ADDED");

        String sessionId = "cs_flow_" + bookingId;
        when(checkoutService.createCheckoutSession(any(), any(), any(), any(), any()))
                .thenReturn(new CheckoutService.CheckoutSession(sessionId, "https://checkout.test/" + bookingId));
        ResponseEntity<JsonNode> payment = post("/bookings/" + bookingId + "/payments", guest, null);
        assertThat(payment.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(payment).path("sessionUrl").asText()).isEqualTo("https://checkout.test/" + bookingId);
        assertThat(bookingStatus(guest, bookingId)).isEqualTo("PAYMENTS_PENDING");

        // Stripe confirms: held rooms become booked
        ResponseEntity<JsonNode> webhook = sendStripeEvent("checkout.session.completed", sessionId, "paid", "complete");
        assertThat(webhook.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(bookingStatus(guest, bookingId)).isEqualTo("CONFIRMED");
        assertThat(bookedCount(hotel.roomId(), checkIn)).isEqualTo(1);
        assertThat(reservedCount(hotel.roomId(), checkIn)).isZero();

        // Stripe retries the same event: nothing changes
        ResponseEntity<JsonNode> retry = sendStripeEvent("checkout.session.completed", sessionId, "paid", "complete");
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(bookedCount(hotel.roomId(), checkIn)).isEqualTo(1);
        assertThat(bookedCount(hotel.roomId(), lastNight)).isEqualTo(1);

        // Cancel: rooms come back and exactly one refund is issued
        ResponseEntity<JsonNode> cancel = post("/bookings/" + bookingId + "/cancel", guest, null);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(bookingStatus(guest, bookingId)).isEqualTo("CANCELLED");
        assertThat(bookedCount(hotel.roomId(), checkIn)).isZero();
        assertThat(bookedCount(hotel.roomId(), lastNight)).isZero();
        // The refund is asynchronous now: the cancellation saves a request, Kafka carries it, a worker calls Stripe.
        verify(checkoutService, timeout(20_000).times(1)).refund(sessionId, bookingId);
    }

    @Test
    void searchHidesAHotelWhoseLastRoomIsHeld() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1500.00");
        assertThat(data(searchHotels(hotel.city(), 1)).path("content")).hasSize(1);

        ResponseEntity<JsonNode> init = initBooking(newGuestToken(), hotel, checkIn, checkOut, 1);
        assertThat(init.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(data(searchHotels(hotel.city(), 1)).path("content")).isEmpty();
        // The departure day was never held, so a stay starting that day is still bookable
        ResponseEntity<JsonNode> nextStay = get("/hotels/search?city=" + hotel.city() + "&startDate=" + checkOut
                + "&endDate=" + checkOut.plusDays(1) + "&roomsCount=1", null);
        assertThat(data(nextStay).path("content")).hasSize(1);
    }

    @Test
    void expiredCheckoutSessionReleasesTheHeldRooms() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");
        String guest = newGuestToken();
        long guestId = addGuest(guest);
        long bookingId = data(initBooking(guest, hotel, checkIn, checkOut, 1)).path("id").asLong();
        post("/bookings/" + bookingId + "/addGuests", guest, List.of(guestId));

        String sessionId = "cs_expired_" + bookingId;
        when(checkoutService.createCheckoutSession(any(), any(), any(), any(), any()))
                .thenReturn(new CheckoutService.CheckoutSession(sessionId, "https://checkout.test/x"));
        post("/bookings/" + bookingId + "/payments", guest, null);
        assertThat(reservedCount(hotel.roomId(), checkIn)).isEqualTo(1);

        ResponseEntity<JsonNode> webhook = sendStripeEvent("checkout.session.expired", sessionId, "unpaid", "expired");

        assertThat(webhook.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(bookingStatus(guest, bookingId)).isEqualTo("EXPIRED");
        assertThat(reservedCount(hotel.roomId(), checkIn)).isZero();
        assertThat(reservedCount(hotel.roomId(), lastNight)).isZero();
    }

    private ResponseEntity<JsonNode> searchHotels(String city, int roomsCount) {
        return get("/hotels/search?city=" + city + "&startDate=" + checkIn + "&endDate=" + checkOut
                + "&roomsCount=" + roomsCount, null);
    }
}
