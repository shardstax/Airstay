package com.shardx.hotelManagement.notification.kafka;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shardx.hotelManagement.notification.event.EventParser;
import com.shardx.hotelManagement.notification.event.MalformedEventException;
import com.shardx.hotelManagement.notification.idempotency.ProcessedEvents;
import com.shardx.hotelManagement.notification.mail.EmailComposer;
import com.shardx.hotelManagement.notification.mail.EmailMessage;
import com.shardx.hotelManagement.notification.mail.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingEventListenerTest {

    @Mock private EmailSender sender;
    @Mock private ProcessedEvents processedEvents;

    private BookingEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new BookingEventListener(new EventParser(JsonMapper.builder().findAndAddModules().build()),
                new EmailComposer(), sender, processedEvents);
    }

    @Test
    void sendsOneEmailAndOnlyThenRemembersTheEvent() {
        listener.onBookingEvent(confirmed("evt-1"));

        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        InOrder order = inOrder(sender, processedEvents);
        order.verify(sender).send(email.capture());
        order.verify(processedEvents).markHandled("evt-1");
        assertThat(email.getValue().to()).isEqualTo("asha@example.com");
        assertThat(email.getValue().subject()).contains("#99").contains("confirmed");
    }

    @Test
    void ignoresAnEventThatWasAlreadyHandled() {
        when(processedEvents.wasHandled("evt-1")).thenReturn(true);

        listener.onBookingEvent(confirmed("evt-1"));

        verify(sender, never()).send(any());
        verify(processedEvents, never()).markHandled(any());
    }

    @Test
    void aFailedSendIsNotRememberedSoTheRetryCanSendIt() {
        doThrow(new MailSendException("SMTP is down")).when(sender).send(any());

        assertThatThrownBy(() -> listener.onBookingEvent(confirmed("evt-1"))).isInstanceOf(MailSendException.class);

        verify(processedEvents, never()).markHandled(any());
    }

    @Test
    void ignoresAnUnknownTypeWithoutTouchingRedisOrMail() {
        listener.onBookingEvent("{\"eventId\":\"evt-1\",\"type\":\"HOTEL_RENAMED\"}");

        verifyNoInteractions(sender, processedEvents);
    }

    @Test
    void neverEmailsARefundRequest() {
        listener.onBookingEvent(event("evt-1", "REFUND_REQUESTED"));

        verifyNoInteractions(sender, processedEvents);
    }

    @Test
    void aMessageThatIsNotJsonIsMalformedSoItGoesStraightToTheDeadLetterTopic() {
        assertThatThrownBy(() -> listener.onBookingEvent("not json")).isInstanceOf(MalformedEventException.class);

        verifyNoInteractions(sender, processedEvents);
    }

    @Test
    void aKafkaTombstoneWithNoContentIsMalformedNotACrash() {
        assertThatThrownBy(() -> listener.onBookingEvent(null)).isInstanceOf(MalformedEventException.class);

        verifyNoInteractions(sender, processedEvents);
    }

    @Test
    void theDeadLetterHandlerLogsTheEventIdButNeverTheMessageWithItsEmailAddress() {
        Logger logger = (Logger) LoggerFactory.getLogger(BookingEventListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            listener.onGaveUp(confirmed("evt-77"), "org.springframework.mail.MailSendException");

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains("evt-77")
                    .contains("MailSendException")
                    .doesNotContain("asha@example.com")
                    .doesNotContain("Asha");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void theDeadLetterHandlerSurvivesAMessageWithoutContent() {
        assertThatCode(() -> listener.onGaveUp(null, null)).doesNotThrowAnyException();
        assertThatCode(() -> listener.onGaveUp("not json", null)).doesNotThrowAnyException();
    }

    @Test
    void aKnownEventWithoutARecipientIsMalformed() {
        assertThatThrownBy(() -> listener.onBookingEvent("""
                {"eventId":"evt-1","type":"BOOKING_CONFIRMED","bookingId":99,"hotelName":"H","roomType":"R",
                 "checkInDate":"2026-12-01","checkOutDate":"2026-12-03","roomsCount":1,"amount":10}
                """)).isInstanceOf(MalformedEventException.class);

        verify(sender, never()).send(any());
    }

    @Test
    void eventsFromALaterApiVersionWithExtraFieldsStillWork() {
        listener.onBookingEvent("""
                {"eventId":"evt-1","type":"BOOKING_CONFIRMED","bookingId":99,"userEmail":"asha@example.com",
                 "userName":"Asha","hotelName":"H","roomType":"R","checkInDate":"2026-12-01",
                 "checkOutDate":"2026-12-03","roomsCount":1,"amount":10,"currency":"INR","loyaltyPoints":50}
                """);

        verify(sender).send(any());
    }

    private static String confirmed(String eventId) {
        return event(eventId, "BOOKING_CONFIRMED");
    }

    private static String event(String eventId, String type) {
        return """
                {"eventId":"%s","type":"%s","occurredAt":"2026-11-17T10:00:00","bookingId":99,"userId":7,
                 "userEmail":"asha@example.com","userName":"Asha","hotelName":"Sea View Resort","roomType":"Deluxe",
                 "checkInDate":"2026-12-01","checkOutDate":"2026-12-03","roomsCount":2,"amount":4000.00}
                """.formatted(eventId, type);
    }
}
