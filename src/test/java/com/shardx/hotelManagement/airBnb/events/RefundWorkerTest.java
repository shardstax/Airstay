package com.shardx.hotelManagement.airBnb.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shardx.hotelManagement.airBnb.entity.Booking;
import com.shardx.hotelManagement.airBnb.exception.PaymentProviderException;
import com.shardx.hotelManagement.airBnb.repository.BookingRepository;
import com.shardx.hotelManagement.airBnb.service.CheckoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundWorkerTest {

    private static final String EVENT_ID = "6f1c6f0a-0000-4000-8000-000000000001";
    private static final String SESSION = "cs_test_1";

    @Mock private CheckoutService checkoutService;
    @Mock private BookingRepository bookingRepository;
    @Mock private OutboxService outboxService;
    @Mock private TransactionTemplate transactions;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private RefundWorker worker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        worker = new RefundWorker(checkoutService, bookingRepository, outboxService, transactions, mapper);
        // Run the transactional block directly, as the real TransactionTemplate would.
        lenient().doAnswer(invocation -> {
            ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
    }

    private String refundRequest(String paymentSessionId) throws Exception {
        return mapper.writeValueAsString(new BookingEvent(EVENT_ID, "REFUND_REQUESTED",
                LocalDateTime.of(2026, 6, 1, 10, 0), 99, 7, "asha@example.com", "Asha", "Sea View", "Deluxe",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), 2, new BigDecimal("4000.00"), paymentSessionId));
    }

    @Test
    void refundsThenRecordsCompletionWithAnIdDerivedFromTheRequest() throws Exception {
        Booking booking = new Booking();
        when(bookingRepository.findById(99L)).thenReturn(Optional.of(booking));

        worker.onRefundRequested(refundRequest(SESSION));

        verify(checkoutService).refund(SESSION, 99L);
        UUID expectedId = UUID.nameUUIDFromBytes(("refund-completed:" + EVENT_ID).getBytes(StandardCharsets.UTF_8));
        verify(outboxService).recordOnce(BookingEventType.REFUND_COMPLETED, booking, expectedId);
    }

    @Test
    void aRedeliveredRequestReusesTheSameCompletionId() throws Exception {
        when(bookingRepository.findById(99L)).thenReturn(Optional.of(new Booking()));

        worker.onRefundRequested(refundRequest(SESSION));
        worker.onRefundRequested(refundRequest(SESSION)); // the same Kafka message delivered again

        // Same id both times, so OutboxService.recordOnce can recognise the second one and record nothing new.
        UUID expectedId = UUID.nameUUIDFromBytes(("refund-completed:" + EVENT_ID).getBytes(StandardCharsets.UTF_8));
        verify(outboxService, times(2)).recordOnce(eq(BookingEventType.REFUND_COMPLETED), any(Booking.class), eq(expectedId));
        verify(checkoutService, times(2)).refund(SESSION, 99L); // safe: Stripe applies the idempotency key
    }

    @Test
    void aStripeFailureIsPropagatedSoTheRetryMachineryEngagesAndNothingIsReportedCompleted() throws Exception {
        doThrow(new PaymentProviderException("stripe is down", new RuntimeException()))
                .when(checkoutService).refund(SESSION, 99L);

        assertThatThrownBy(() -> worker.onRefundRequested(refundRequest(SESSION)))
                .isInstanceOf(PaymentProviderException.class);

        verifyNoInteractions(outboxService);
    }

    @Test
    void aRequestWithoutAPaymentSessionIsHopelessAndNeverCallsStripe() throws Exception {
        assertThatThrownBy(() -> worker.onRefundRequested(refundRequest(null)))
                .isInstanceOf(IllegalArgumentException.class); // IllegalArgumentException is excluded from retries

        verifyNoInteractions(checkoutService, outboxService);
    }

    @Test
    void anUnreadableMessageIsHopelessToo() {
        assertThatThrownBy(() -> worker.onRefundRequested("this is not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unreadable refund request");

        verifyNoInteractions(checkoutService, outboxService);
    }

    @Test
    void aMissingBookingIsAFailureThatGetsRetriedNotSilentlyIgnored() throws Exception {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> worker.onRefundRequested(refundRequest(SESSION)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(mapper).isNotNull();
    }
}
