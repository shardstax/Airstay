package com.shardx.hotelManagement.airBnb.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardx.hotelManagement.airBnb.entity.Booking;
import com.shardx.hotelManagement.airBnb.repository.BookingRepository;
import com.shardx.hotelManagement.airBnb.service.BookingExpiryScheduler;
import com.shardx.hotelManagement.airBnb.service.CheckoutService;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
        Confirmed confirmed = confirmedBooking();

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
        Confirmed confirmed = confirmedBooking();
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
        Confirmed confirmed = confirmedBooking();

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
        Confirmed confirmed;
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
        try {
            confirmed = confirmedBooking();

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

    private record Confirmed(long bookingId, String email, String token) {
    }

    /** Runs the whole guest journey up to a Stripe-confirmed booking. */
    private Confirmed confirmedBooking() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 2, "1000.00");
        String email = "guest-" + UUID.randomUUID() + "@example.com";
        post("/auth/signup", null, Map.of("email", email, "password", PASSWORD, "name", "Event Guest"));
        String token = data(post("/auth/login", null, Map.of("email", email, "password", PASSWORD)))
                .path("accessToken").asText();
        long guestId = addGuest(token);
        long bookingId = data(initBooking(token, hotel, checkIn, checkOut, 1)).path("id").asLong();
        post("/bookings/" + bookingId + "/addGuests", token, List.of(guestId));

        String sessionId = "cs_outbox_" + bookingId;
        when(checkoutService.createCheckoutSession(any(), any(), any(), any(), any()))
                .thenReturn(new CheckoutService.CheckoutSession(sessionId, "https://checkout.test/" + bookingId));
        post("/bookings/" + bookingId + "/payments", token, null);
        assertThat(sendStripeEvent("checkout.session.completed", sessionId, "paid", "complete").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        return new Confirmed(bookingId, email, token);
    }

    private int eventRows(long bookingId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_key = ? AND event_type = ?",
                Integer.class, String.valueOf(bookingId), type);
    }

    private boolean isPublished(long bookingId, String type) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT published_at IS NOT NULL FROM outbox_event WHERE event_key = ? AND event_type = ?",
                Boolean.class, String.valueOf(bookingId), type));
    }

    /** Reads the topic from the beginning with a fresh consumer group until the wanted event shows up. */
    private JsonNode awaitEvent(String topic, long bookingId, String type) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 40_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    Header typeHeader = record.headers().lastHeader("event-type");
                    if (String.valueOf(bookingId).equals(record.key()) && typeHeader != null
                            && type.equals(new String(typeHeader.value(), StandardCharsets.UTF_8))) {
                        return objectMapper.readTree(record.value());
                    }
                }
            }
        } catch (Exception e) {
            throw new AssertionError("Failed while reading " + topic, e);
        }
        throw new AssertionError("No " + type + " event for booking " + bookingId + " arrived on " + topic);
    }
}
