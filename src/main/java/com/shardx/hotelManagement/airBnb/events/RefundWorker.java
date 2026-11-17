package com.shardx.hotelManagement.airBnb.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardx.hotelManagement.airBnb.entity.Booking;
import com.shardx.hotelManagement.airBnb.repository.BookingRepository;
import com.shardx.hotelManagement.airBnb.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Gives payments back. It consumes {@code REFUND_REQUESTED} events that were saved in the outbox in the same
 * transaction as the cancellation, so a cancelled booking always ends up with a refund request, and a failing Stripe
 * can no longer lose the refund (it used to be logged for a manual retry).
 *
 * <ul>
 *   <li><b>Retries without blocking:</b> a failed attempt moves the message to a retry topic with a growing delay, so a
 *       Stripe outage does not stall other refunds. After the last attempt it lands in the dead-letter topic
 *       ({@code airstay.refund-requests.DLT}) for a human.</li>
 *   <li><b>Safe to repeat:</b> Stripe gets an idempotency key per booking, so an attempt that already succeeded is a
 *       no-op. The "refund completed" event gets an id derived from the request, so a duplicate delivery cannot
 *       produce a second one.</li>
 *   <li><b>No retry for hopeless input:</b> an unreadable or incomplete request goes straight to the dead-letter topic.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundWorker {

    private final CheckoutService checkoutService;
    private final BookingRepository bookingRepository;
    private final OutboxService outboxService;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "${airstay.events.refund-attempts}",
            backoff = @Backoff(
                    delayExpression = "${airstay.events.refund-retry-delay-ms}",
                    multiplierExpression = "${airstay.events.refund-retry-multiplier}",
                    maxDelayExpression = "${airstay.events.refund-retry-max-delay-ms}"),
            exclude = IllegalArgumentException.class,
            dltTopicSuffix = ".DLT",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            numPartitions = "3",
            replicationFactor = "1")
    @KafkaListener(topics = Topics.REFUND_REQUESTS, groupId = "airstay-refund-worker",
            autoStartup = "${airstay.events.enabled}")
    public void onRefundRequested(String payload) {
        BookingEvent request = parse(payload);
        if (request.paymentSessionId() == null || request.paymentSessionId().isBlank()) {
            throw new IllegalArgumentException("Refund request for booking " + request.bookingId()
                    + " has no payment session");
        }

        // Stripe call first, with an idempotency key per booking: repeating it after a success changes nothing.
        checkoutService.refund(request.paymentSessionId(), request.bookingId());

        UUID completedEventId = UUID.nameUUIDFromBytes(
                ("refund-completed:" + request.eventId()).getBytes(StandardCharsets.UTF_8));
        transactions.executeWithoutResult(status -> {
            Booking booking = bookingRepository.findById(request.bookingId()).orElseThrow(() ->
                    new IllegalStateException("Booking " + request.bookingId() + " not found for its refund"));
            outboxService.recordOnce(BookingEventType.REFUND_COMPLETED, booking, completedEventId);
        });
        log.info("Refund completed for booking {}", request.bookingId());
    }

    /** Every attempt failed (or the request was hopeless): nothing more happens automatically. */
    @DltHandler
    public void onRefundGaveUp(String payload,
                               @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String reason) {
        log.error("REFUND_DEAD_LETTER: a refund could not be completed and needs manual action. Reason: {}. Request: {}",
                reason, payload);
    }

    private BookingEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, BookingEvent.class);
        } catch (JsonProcessingException e) {
            // Retrying cannot make a malformed message readable: IllegalArgumentException skips the retries.
            throw new IllegalArgumentException("Unreadable refund request: " + e.getOriginalMessage(), e);
        }
    }
}
