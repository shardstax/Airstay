package com.shardx.hotelManagement.airBnb.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shardx.hotelManagement.airBnb.entity.Booking;
import com.shardx.hotelManagement.airBnb.entity.Hotel;
import com.shardx.hotelManagement.airBnb.entity.Room;
import com.shardx.hotelManagement.airBnb.entity.User;
import com.shardx.hotelManagement.airBnb.entity.enums.BookingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** The JSON of these events is a contract with other services (docs/events.md): names and formats must not drift. */
class BookingEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 10, 0);

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Booking booking() {
        User user = new User();
        user.setId(7L);
        user.setEmail("asha@example.com");
        user.setName("Asha");
        Hotel hotel = new Hotel();
        hotel.setName("Sea View Resort");
        Room room = new Room();
        room.setType("Deluxe");
        return Booking.builder()
                .id(99L).user(user).hotel(hotel).room(room)
                .checkInDate(LocalDate.of(2026, 7, 1)).checkOutDate(LocalDate.of(2026, 7, 3))
                .roomsCount(2).amount(new BigDecimal("4000.00"))
                .bookingStatus(BookingStatus.CONFIRMED).paymentSessionId("cs_test_1")
                .build();
    }

    private JsonNode json(BookingEventType type) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(BookingEvent.of(type, booking(), NOW)));
    }

    @Test
    void carriesEverythingAConsumerNeedsWithoutCallingBack() throws Exception {
        JsonNode event = json(BookingEventType.BOOKING_CONFIRMED);

        assertThat(event.path("type").asText()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(event.path("occurredAt").asText()).isEqualTo("2026-06-01T10:00:00");
        assertThat(event.path("bookingId").asLong()).isEqualTo(99);
        assertThat(event.path("userId").asLong()).isEqualTo(7);
        assertThat(event.path("userEmail").asText()).isEqualTo("asha@example.com");
        assertThat(event.path("userName").asText()).isEqualTo("Asha");
        assertThat(event.path("hotelName").asText()).isEqualTo("Sea View Resort");
        assertThat(event.path("roomType").asText()).isEqualTo("Deluxe");
        assertThat(event.path("checkInDate").asText()).isEqualTo("2026-07-01");
        assertThat(event.path("checkOutDate").asText()).isEqualTo("2026-07-03");
        assertThat(event.path("roomsCount").asInt()).isEqualTo(2);
        assertThat(event.path("amount").decimalValue()).isEqualByComparingTo("4000.00");
        assertThat(event.path("eventId").asText()).matches("[0-9a-f-]{36}");
    }

    @Test
    void onlyRefundRequestsCarryThePaymentSession() throws Exception {
        assertThat(json(BookingEventType.REFUND_REQUESTED).path("paymentSessionId").asText()).isEqualTo("cs_test_1");
        for (BookingEventType type : new BookingEventType[]{BookingEventType.BOOKING_CONFIRMED,
                BookingEventType.BOOKING_CANCELLED, BookingEventType.BOOKING_EXPIRED, BookingEventType.REFUND_COMPLETED}) {
            assertThat(json(type).path("paymentSessionId").isNull()).as("%s must not leak the payment session", type)
                    .isTrue();
        }
    }

    @Test
    void everyEventGetsItsOwnId() {
        assertThat(BookingEvent.of(BookingEventType.BOOKING_CONFIRMED, booking(), NOW).eventId())
                .isNotEqualTo(BookingEvent.of(BookingEventType.BOOKING_CONFIRMED, booking(), NOW).eventId());
    }

    @Test
    void eventsAreRoutedToTheRightTopic() {
        assertThat(BookingEventType.BOOKING_CONFIRMED.topic()).isEqualTo(Topics.BOOKING_EVENTS);
        assertThat(BookingEventType.BOOKING_CANCELLED.topic()).isEqualTo(Topics.BOOKING_EVENTS);
        assertThat(BookingEventType.BOOKING_EXPIRED.topic()).isEqualTo(Topics.BOOKING_EVENTS);
        assertThat(BookingEventType.REFUND_COMPLETED.topic()).isEqualTo(Topics.BOOKING_EVENTS);
        assertThat(BookingEventType.REFUND_REQUESTED.topic()).isEqualTo(Topics.REFUND_REQUESTS);
    }
}
