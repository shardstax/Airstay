package com.shardx.hotelManagement.notification.kafka;

import com.shardx.hotelManagement.notification.event.BookingEvent;
import com.shardx.hotelManagement.notification.event.EventParser;
import com.shardx.hotelManagement.notification.event.EventTypes;
import com.shardx.hotelManagement.notification.event.MalformedEventException;
import com.shardx.hotelManagement.notification.idempotency.ProcessedEvents;
import com.shardx.hotelManagement.notification.mail.EmailComposer;
import com.shardx.hotelManagement.notification.mail.EmailMessage;
import com.shardx.hotelManagement.notification.mail.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.mail.MailParseException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends an e-mail for every booking event.
 *
 * <ul>
 *   <li><b>Unknown types are ignored</b> (forward compatible: AirStay may publish new event types).</li>
 *   <li><b>Idempotent:</b> an event id that was already e-mailed is skipped. The id is remembered only AFTER the send
 *       succeeded, so a failed send is retried instead of being lost.</li>
 *   <li><b>Failures never block other messages:</b> an SMTP error moves the message to a retry topic with a growing
 *       delay (non-blocking) and, after the last attempt, to the dead-letter topic
 *       {@code airstay.booking-events.notifications.DLT}.</li>
 *   <li><b>No retry for hopeless input:</b> a message that is not JSON, or lacks what the e-mail needs, goes straight
 *       to the dead-letter topic.</li>
 * </ul>
 * The listener never logs e-mail addresses.
 */
@Component
public class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);
    private static final Pattern EVENT_ID = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"\\\\]{1,64})\"");

    private final EventParser parser;
    private final EmailComposer composer;
    private final EmailSender sender;
    private final ProcessedEvents processedEvents;

    public BookingEventListener(EventParser parser, EmailComposer composer, EmailSender sender,
                                ProcessedEvents processedEvents) {
        this.parser = parser;
        this.composer = composer;
        this.sender = sender;
        this.processedEvents = processedEvents;
    }

    @RetryableTopic(
            attempts = "${airstay.notifications.retry-attempts}",
            backoff = @Backoff(
                    delayExpression = "${airstay.notifications.retry-delay-ms}",
                    multiplierExpression = "${airstay.notifications.retry-multiplier}",
                    maxDelayExpression = "${airstay.notifications.retry-max-delay-ms}"),
            // MailParseException: the address itself is invalid, which no number of retries can fix.
            exclude = {MalformedEventException.class, MailParseException.class},
            retryTopicSuffix = ".notifications.retry",
            dltTopicSuffix = ".notifications.DLT",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            numPartitions = "3",
            replicationFactor = "1")
    @KafkaListener(topics = Topics.BOOKING_EVENTS, groupId = "airstay-notifications")
    public void onBookingEvent(@Payload(required = false) String payload) {
        BookingEvent event = parser.parse(payload);

        if (!EventTypes.isNotifiable(event.type())) {
            log.debug("Ignoring event {} of type {}", event.eventId(), event.type());
            return;
        }
        if (processedEvents.wasHandled(event.eventId())) {
            log.info("Event {} ({}) was already e-mailed, ignoring the duplicate", event.eventId(), event.type());
            return;
        }

        EmailMessage email = composer.compose(event).orElseThrow();
        sender.send(email);
        processedEvents.markHandled(event.eventId());
        log.info("Sent {} e-mail for booking {} (event {})", event.type(), event.bookingId(), event.eventId());
    }

    /**
     * Every attempt failed (or the message was hopeless): nothing more happens automatically.
     *
     * <p>Logs the event id and the kind of error only. The message itself stays in the dead-letter topic (see
     * docs/events.md for how to read it) and is NOT logged, because it holds the guest's e-mail address; the exception
     * text is not logged either, because mail errors often quote the recipient.
     */
    @DltHandler
    public void onGaveUp(@Payload(required = false) String payload,
                         @Header(name = KafkaHeaders.EXCEPTION_FQCN, required = false) String errorType) {
        log.error("NOTIFICATION_DEAD_LETTER: an event could not be e-mailed and needs a look. eventId={}, error={}. "
                + "The message is in the topic airstay.booking-events.notifications.DLT.", eventIdOf(payload), errorType);
    }

    /** The event id, if the message has one that can be read without a JSON parser (the message may be garbage). */
    private static String eventIdOf(String payload) {
        if (payload == null) {
            return "unknown";
        }
        Matcher matcher = EVENT_ID.matcher(payload);
        return matcher.find() ? matcher.group(1) : "unknown";
    }
}
