package com.shardx.hotelManagement.airBnb.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardx.hotelManagement.airBnb.exception.PaymentProviderException;
import com.shardx.hotelManagement.airBnb.service.BookingExpiryScheduler;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import com.shardx.hotelManagement.airBnb.support.KafkaTopicReader;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Refunds end to end: cancellation saves a refund request in the outbox, Kafka carries it, the RefundWorker calls
 * Stripe (mocked here) with retries, and a refund that never succeeds ends in the dead-letter topic.
 * Retries are 300 ms apart and there are 4 attempts in total (see application-test.properties).
 */
class RefundFlowIT extends IntegrationTestBase {

    private static final Duration PATIENCE = Duration.ofSeconds(40);

    @Autowired private KafkaContainer kafka;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private BookingExpiryScheduler expiryScheduler;

    private KafkaTopicReader reader;
    private final LocalDate checkIn = LocalDate.now().plusDays(95);
    private final LocalDate checkOut = checkIn.plusDays(2);

    @BeforeEach
    void createReader() {
        reader = new KafkaTopicReader(kafka.getBootstrapServers(), objectMapper);
    }

    @Test
    void cancellingARefundsThroughTheWorkerAndPublishesRefundCompleted() {
        ConfirmedBooking booking = createConfirmedBooking(checkIn, checkOut);

        assertThat(post("/bookings/" + booking.bookingId() + "/cancel", booking.token(), null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        verify(checkoutService, timeout(20_000).times(1)).refund(booking.sessionId(), booking.bookingId());
        JsonNode completed = reader.awaitEvent(Topics.BOOKING_EVENTS, booking.bookingId(), "REFUND_COMPLETED");
        assertThat(completed.path("userEmail").asText()).isEqualTo(booking.email());
        assertThat(completed.path("amount").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(rows(booking.bookingId(), "REFUND_COMPLETED")).as("exactly one completion event").isEqualTo(1);
    }

    @Test
    void aFailingStripeIsRetriedUntilTheRefundGoesThrough() {
        ConfirmedBooking booking = createConfirmedBooking(checkIn, checkOut);
        doThrow(new PaymentProviderException("stripe is down", new RuntimeException()))
                .doThrow(new PaymentProviderException("stripe is still down", new RuntimeException()))
                .doNothing()
                .when(checkoutService).refund(booking.sessionId(), booking.bookingId());

        post("/bookings/" + booking.bookingId() + "/cancel", booking.token(), null);

        // Two failures, then success on the third attempt.
        verify(checkoutService, timeout(30_000).times(3)).refund(booking.sessionId(), booking.bookingId());
        reader.awaitEvent(Topics.BOOKING_EVENTS, booking.bookingId(), "REFUND_COMPLETED");
        assertThat(rows(booking.bookingId(), "REFUND_COMPLETED")).isEqualTo(1);
    }

    @Test
    void aRefundThatNeverSucceedsEndsInTheDeadLetterTopicAndIsNotReportedCompleted() throws Exception {
        ConfirmedBooking booking = createConfirmedBooking(checkIn, checkOut);
        doThrow(new PaymentProviderException("stripe is down for good", new RuntimeException()))
                .when(checkoutService).refund(booking.sessionId(), booking.bookingId());

        post("/bookings/" + booking.bookingId() + "/cancel", booking.token(), null);

        // 4 attempts in total, then it gives up.
        verify(checkoutService, timeout(30_000).times(4)).refund(booking.sessionId(), booking.bookingId());
        ConsumerRecord<String, String> deadLetter = reader.awaitRecord(Topics.REFUND_REQUESTS_DLT,
                String.valueOf(booking.bookingId()), null, PATIENCE);
        JsonNode request = objectMapper.readTree(deadLetter.value());
        assertThat(request.path("type").asText()).isEqualTo("REFUND_REQUESTED");
        assertThat(request.path("paymentSessionId").asText()).isEqualTo(booking.sessionId());

        Thread.sleep(1500);
        verify(checkoutService, times(4)).refund(booking.sessionId(), booking.bookingId()); // no fifth attempt
        assertThat(rows(booking.bookingId(), "REFUND_COMPLETED")).as("must not claim a refund that did not happen").isZero();
    }

    @Test
    void paymentThatArrivesForAnExpiredBookingIsRefundedToo() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");
        String guest = newGuestToken();
        long bookingId = data(initBooking(guest, hotel, checkIn.plusDays(10), checkOut.plusDays(10), 1)).path("id").asLong();
        post("/bookings/" + bookingId + "/addGuests", guest, List.of(addGuest(guest)));
        String sessionId = "cs_late_" + bookingId;
        org.mockito.Mockito.when(checkoutService.createCheckoutSession(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.shardx.hotelManagement.airBnb.service.CheckoutService.CheckoutSession(sessionId, "https://x"));
        post("/bookings/" + bookingId + "/payments", guest, null);

        // The hold lapses and is released...
        jdbc.update("UPDATE booking SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), bookingId);
        expiryScheduler.releaseExpiredHolds();
        assertThat(bookingStatus(guest, bookingId)).isEqualTo("EXPIRED");
        // ...and then the guest's payment arrives anyway.
        assertThat(sendStripeEvent("checkout.session.completed", sessionId, "paid", "complete").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        verify(checkoutService, timeout(20_000).times(1)).refund(sessionId, bookingId);
        assertThat(bookingStatus(guest, bookingId)).as("the booking is not revived").isEqualTo("EXPIRED");
    }

    @Test
    void anUnreadableRequestGoesStraightToTheDeadLetterTopicWithoutRetries() {
        kafkaTemplate.send(Topics.REFUND_REQUESTS, "bad-message-1", "this is not json");

        ConsumerRecord<String, String> deadLetter =
                reader.awaitRecord(Topics.REFUND_REQUESTS_DLT, "bad-message-1", null, PATIENCE);

        assertThat(deadLetter.value()).isEqualTo("this is not json");
        verify(checkoutService, never()).refund(anyString(), anyLong());
    }

    private int rows(long bookingId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_key = ? AND event_type = ?",
                Integer.class, String.valueOf(bookingId), type);
    }
}
