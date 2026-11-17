package com.shardx.hotelManagement.airBnb.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardx.hotelManagement.airBnb.entity.Booking;
import com.shardx.hotelManagement.airBnb.repository.BookingRepository;
import com.shardx.hotelManagement.airBnb.service.BookingExpiryScheduler;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import com.shardx.hotelManagement.airBnb.support.KafkaTopicReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** The transactional outbox and its relay against real PostgreSQL and a real Kafka broker. */
class OutboxIT extends IntegrationTestBase {

    @Autowired private KafkaContainer kafka;
    @Autowired private OutboxService outboxService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private TransactionTemplate transactions;
    @Autowired private BookingExpiryScheduler expiryScheduler;
    @Autowired private ObjectMapper objectMapper;

    private final LocalDate checkIn = LocalDate.now().plusDays(80);
    private final LocalDate checkOut = checkIn.plusDays(2);

    @Test
    void aConfirmedBookingIsPublishedToKafkaWithEverythingAConsumerNeeds() {
        ConfirmedBooking confirmed = createConfirmedBooking(checkIn, checkOut);

        JsonNode event = awaitEvent(Topics.BOOKING_EVENTS, confirmed.bookingId(), "BOOKING_CONFIRMED");

        assertThat(event.path("userEmail").asText()).isEqualTo(confirmed.email());
        assertThat(event.path("hotelName").asText()).isEqualTo("Test Hotel");
        assertThat(event.path("roomType").asText()).isEqualTo("Deluxe");
        assertThat(event.path("checkInDate").asText()).isEqualTo(checkIn.toString());
        assertThat(event.path("checkOutDate").asText()).isEqualTo(checkOut.toString());
        assertThat(event.path("amount").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(event.path("paymentSessionId").isNull()).as("payment details stay out of the public topic").isTrue();
        assertThat(isPublished(confirmed.bookingId(), "BOOKING_CONFIRMED")).isTrue();
    }

    @Test
    void cancellationAndExpiryArePublishedToo() {
        ConfirmedBooking confirmed = createConfirmedBooking(checkIn, checkOut);
        assertThat(post("/bookings/" + confirmed.bookingId() + "/cancel", confirmed.token(), null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(awaitEvent(Topics.BOOKING_EVENTS, confirmed.bookingId(), "BOOKING_CANCELLED")
                .path("bookingId").asLong()).isEqualTo(confirmed.bookingId());

        // An abandoned hold lapses and is released by the expiry job.
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");
        String guest = newGuestToken();
        long heldId = data(initBooking(guest, hotel, checkIn.plusDays(10), checkOut.plusDays(10), 1)).path("id").asLong();
        jdbc.update("UPDATE booking SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), heldId);
        expiryScheduler.releaseExpiredHolds();

        assertThat(awaitEvent(Topics.BOOKING_EVENTS, heldId, "BOOKING_EXPIRED").path("bookingId").asLong())
                .isEqualTo(heldId);
    }

    @Test
    void anEventIsRolledBackTogetherWithTheChangeThatCausedIt() {
        ConfirmedBooking confirmed = createConfirmedBooking(checkIn, checkOut);

        assertThatThrownBy(() -> transactions.execute(status -> {
            Booking booking = bookingRepository.findById(confirmed.bookingId()).orElseThrow();
            outboxService.record(BookingEventType.BOOKING_CANCELLED, booking);
            throw new IllegalStateException("simulated failure after the event was recorded");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(eventRows(confirmed.bookingId(), "BOOKING_CANCELLED"))
                .as("no event may survive a rolled-back transaction").isZero();
    }

    @Test
    void recordingAnEventOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> outboxService.record(BookingEventType.BOOKING_CONFIRMED, new Booking()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void aRefusedChangeLeavesNoEventBehind() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");
        String guest = newGuestToken();
        long bookingId = data(initBooking(guest, hotel, checkIn, checkOut, 1)).path("id").asLong();

        // The booking is only RESERVED, so cancelling is refused with 409 and nothing may be published.
        assertThat(post("/bookings/" + bookingId + "/cancel", guest, null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(eventRows(bookingId, "BOOKING_CANCELLED")).isZero();
    }

    /**
     * Failure injection: the Kafka broker is frozen (docker pause) while a booking is confirmed. The booking must work
     * normally, the event must wait in the outbox, and it must arrive once Kafka is back.
     */
    @Test
    void bookingsKeepWorkingWhileKafkaIsDownAndTheEventArrivesWhenItReturns() throws Exception {
        ConfirmedBooking confirmed;
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
        try {
            confirmed = createConfirmedBooking(checkIn, checkOut);

            assertThat(bookingStatus(confirmed.token(), confirmed.bookingId())).as("booking works without Kafka")
                    .isEqualTo("CONFIRMED");
            Thread.sleep(2500); // several relay rounds while Kafka is unreachable
            assertThat(eventRows(confirmed.bookingId(), "BOOKING_CONFIRMED")).as("event is safe in the outbox").isEqualTo(1);
            assertThat(isPublished(confirmed.bookingId(), "BOOKING_CONFIRMED")).as("but not published yet").isFalse();
        } finally {
            kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
        }

        JsonNode event = awaitEvent(Topics.BOOKING_EVENTS, confirmed.bookingId(), "BOOKING_CONFIRMED");
        assertThat(event.path("bookingId").asLong()).isEqualTo(confirmed.bookingId());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(isPublished(confirmed.bookingId(), "BOOKING_CONFIRMED")).isTrue());
    }

    // ---------------------------------------------------------------------------------------------

    private int eventRows(long bookingId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_key = ? AND event_type = ?",
                Integer.class, String.valueOf(bookingId), type);
    }

    private boolean isPublished(long bookingId, String type) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT published_at IS NOT NULL FROM outbox_event WHERE event_key = ? AND event_type = ?",
                Boolean.class, String.valueOf(bookingId), type));
    }

    private JsonNode awaitEvent(String topic, long bookingId, String type) {
        return new KafkaTopicReader(kafka.getBootstrapServers(), objectMapper).awaitEvent(topic, bookingId, type);
    }
}
