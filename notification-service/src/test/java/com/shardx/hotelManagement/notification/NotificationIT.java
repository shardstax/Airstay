package com.shardx.hotelManagement.notification;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.shardx.hotelManagement.notification.kafka.Topics;
import com.shardx.hotelManagement.notification.support.KafkaTopicReader;
import com.shardx.hotelManagement.notification.support.TestcontainersConfiguration;
import jakarta.mail.Address;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole service against real Kafka and Redis (Testcontainers) and an in-process SMTP server (GreenMail): events
 * are published to the real topic and e-mails are read from the mail server's mailboxes.
 *
 * <p>Every test uses its own recipient address and booking id, so tests never see each other's e-mail. To prove that a
 * message was NOT e-mailed, a test publishes a marker event with the same Kafka key afterwards: events with one key
 * stay in order on one partition, so once the marker's e-mail has arrived the earlier message has been handled.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NotificationIT {

    private static final String RETRY_TOPIC_0 = Topics.BOOKING_EVENTS + ".notifications.retry-0";
    private static final String DEAD_LETTER_TOPIC = Topics.BOOKING_EVENTS + ".notifications.DLT";
    private static final Duration WAIT = Duration.ofSeconds(30);

    private static final int SMTP_PORT = freePort();
    private static final GreenMail SMTP_SERVER =
            new GreenMail(new ServerSetup(SMTP_PORT, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
    private static final AtomicLong BOOKING_IDS = new AtomicLong(1_000 + (System.nanoTime() % 100_000));

    static {
        SMTP_SERVER.start();
    }

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
    }

    @AfterAll
    static void stopSmtpServer() {
        SMTP_SERVER.stop();
    }

    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private KafkaContainer kafkaContainer;
    @Autowired private JavaMailSender mailSender;

    // ---- the normal path --------------------------------------------------------------------------

    @Test
    void aConfirmedBookingSendsOneEmailWithTheRightContent() throws Exception {
        String guest = uniqueEmail("confirmed");
        long bookingId = nextBookingId();

        publish(bookingId, event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", guest, bookingId));

        MimeMessage email = awaitEmailTo(guest);
        assertThat(email.getSubject()).isEqualTo("Your AirStay booking #" + bookingId + " is confirmed");
        assertThat(email.getFrom()[0].toString()).contains("no-reply@airstay.local");
        assertThat(GreenMailUtil.getBody(email))
                .contains("Hi Asha,")
                .contains("Hotel:      Sea View Resort")
                .contains("Room:       Deluxe x 2")
                .contains("Check-in:   2026-12-01")
                .contains("Check-out:  2026-12-03 (departure day)")
                .contains("Nights:     2")
                .contains("Amount:     INR 4000.00");
        assertThat(emailsTo(guest)).hasSize(1);
    }

    @Test
    void everyNotifiableTypeSendsItsOwnEmail() throws Exception {
        String guest = uniqueEmail("types");
        long bookingId = nextBookingId();

        for (String type : List.of("BOOKING_CONFIRMED", "BOOKING_CANCELLED", "BOOKING_EXPIRED", "REFUND_COMPLETED")) {
            publish(bookingId, event(UUID.randomUUID().toString(), type, guest, bookingId));
        }

        await().atMost(WAIT).untilAsserted(() -> assertThat(emailsTo(guest)).hasSize(4));
        assertThat(emailsTo(guest).stream().map(NotificationIT::subjectOf)).containsExactlyInAnyOrder(
                "Your AirStay booking #" + bookingId + " is confirmed",
                "Your AirStay booking #" + bookingId + " was cancelled",
                "Your AirStay hold for booking #" + bookingId + " expired",
                "Your refund for AirStay booking #" + bookingId + " has been issued");
    }

    // ---- idempotency and forward compatibility -------------------------------------------------------

    @Test
    void theSameEventDeliveredTwiceSendsOnlyOneEmail() throws Exception {
        String guest = uniqueEmail("duplicate");
        long bookingId = nextBookingId();
        String eventId = UUID.randomUUID().toString();
        String payload = event(eventId, "BOOKING_CONFIRMED", guest, bookingId);

        publish(bookingId, payload);
        awaitEmailTo(guest);
        publish(bookingId, payload); // Kafka redelivers the same event (same eventId)
        awaitMarkerAfter(bookingId);

        assertThat(emailsTo(guest)).hasSize(1);
    }

    @Test
    void unknownAndInternalEventTypesAreIgnored() throws Exception {
        String guest = uniqueEmail("ignored");
        long bookingId = nextBookingId();

        publish(bookingId, event(UUID.randomUUID().toString(), "HOTEL_RENAMED", guest, bookingId));
        // REFUND_REQUESTED is work for AirStay's refund worker; it must never reach a guest, even by mistake.
        publish(bookingId, event(UUID.randomUUID().toString(), "REFUND_REQUESTED", guest, bookingId));
        awaitMarkerAfter(bookingId);

        assertThat(emailsTo(guest)).isEmpty();
    }

    @Test
    void anEventWithFieldsFromALaterApiVersionIsStillEmailed() throws Exception {
        String guest = uniqueEmail("future");
        long bookingId = nextBookingId();
        String payload = event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", guest, bookingId)
                .replaceFirst("\\}\\s*$", ",\"currency\":\"INR\",\"loyaltyPoints\":50,\"extra\":{\"a\":[1]}}");

        publish(bookingId, payload);

        assertThat(awaitEmailTo(guest).getSubject()).contains("confirmed");
    }

    // ---- hopeless messages go straight to the dead-letter topic ------------------------------------------

    @Test
    void aMessageThatIsNotJsonGoesStraightToTheDeadLetterTopicWithoutRetries() throws Exception {
        long bookingId = nextBookingId();
        String garbage = "this is not json " + UUID.randomUUID();

        publish(bookingId, garbage);

        assertThat(reader().awaitRecord(DEAD_LETTER_TOPIC, garbage::equals, WAIT)).isPresent();
        awaitMarkerAfter(bookingId);
        assertThat(reader().awaitRecord(RETRY_TOPIC_0, garbage::equals, Duration.ofSeconds(2)))
                .as("a hopeless message must skip the retry topics").isEmpty();
    }

    @Test
    void aKnownEventWithoutARecipientGoesStraightToTheDeadLetterTopic() throws Exception {
        long bookingId = nextBookingId();
        String eventId = UUID.randomUUID().toString();
        String noRecipient = event(eventId, "BOOKING_CONFIRMED", "x@example.test", bookingId)
                .replace("\"userEmail\":\"x@example.test\",", "");

        publish(bookingId, noRecipient);

        assertThat(reader().awaitRecord(DEAD_LETTER_TOPIC, value -> value.contains(eventId), WAIT)).isPresent();
        awaitMarkerAfter(bookingId);
        assertThat(reader().awaitRecord(RETRY_TOPIC_0, value -> value.contains(eventId), Duration.ofSeconds(2)))
                .isEmpty();
    }

    // ---- a failing mail server ---------------------------------------------------------------------------

    @Test
    void aFailingMailServerIsRetriedAndTheEmailArrivesWhenItIsBack() throws Exception {
        String guest = uniqueEmail("retry");
        long bookingId = nextBookingId();
        String eventId = UUID.randomUUID().toString();
        JavaMailSenderImpl smtp = (JavaMailSenderImpl) mailSender;

        smtp.setPort(unusedPort()); // nothing listens there: every send fails
        try {
            publish(bookingId, event(eventId, "BOOKING_CONFIRMED", guest, bookingId));
            // The first attempt failed, so the message moved to the first retry topic instead of being dropped.
            assertThat(reader().awaitRecord(RETRY_TOPIC_0, value -> value.contains(eventId), WAIT)).isPresent();
            assertThat(emailsTo(guest)).isEmpty();
        } finally {
            smtp.setPort(SMTP_PORT); // the mail server is back
        }

        assertThat(awaitEmailTo(guest).getSubject()).contains("confirmed");
        assertThat(emailsTo(guest)).hasSize(1);
    }

    @Test
    void aMailServerThatNeverComesBackEndsInTheDeadLetterTopicAndDoesNotBlockOtherMessages() throws Exception {
        String guest = uniqueEmail("giveup");
        long bookingId = nextBookingId();
        String eventId = UUID.randomUUID().toString();
        JavaMailSenderImpl smtp = (JavaMailSenderImpl) mailSender;

        smtp.setPort(unusedPort());
        try {
            publish(bookingId, event(eventId, "BOOKING_CONFIRMED", guest, bookingId));
            assertThat(reader().awaitRecord(RETRY_TOPIC_0, value -> value.contains(eventId), WAIT)).isPresent();

            // While that message is waiting for its next attempt, other messages still flow through the consumer.
            String garbage = "garbage while retrying " + UUID.randomUUID();
            publish(nextBookingId(), garbage);
            assertThat(reader().awaitRecord(DEAD_LETTER_TOPIC, garbage::equals, WAIT)).isPresent();
            assertThat(reader().awaitRecord(DEAD_LETTER_TOPIC, value -> value.contains(eventId), Duration.ofMillis(500)))
                    .as("the failing event is still being retried, not dead-lettered yet").isEmpty();

            // After every attempt failed it lands in the dead-letter topic, and no e-mail was sent.
            assertThat(reader().awaitRecord(DEAD_LETTER_TOPIC, value -> value.contains(eventId), WAIT)).isPresent();
            assertThat(emailsTo(guest)).isEmpty();
        } finally {
            smtp.setPort(SMTP_PORT);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private void publish(long key, String payload) throws Exception {
        kafka.send(Topics.BOOKING_EVENTS, String.valueOf(key), payload).get(10, TimeUnit.SECONDS);
    }

    /** Publishes an event with the same key and waits for its e-mail: everything published earlier with that key is done. */
    private void awaitMarkerAfter(long bookingId) throws Exception {
        String marker = uniqueEmail("marker");
        publish(bookingId, event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", marker, bookingId));
        awaitEmailTo(marker);
    }

    private KafkaTopicReader reader() {
        return new KafkaTopicReader(kafkaContainer.getBootstrapServers());
    }

    private MimeMessage awaitEmailTo(String recipient) {
        await().atMost(WAIT).until(() -> !emailsTo(recipient).isEmpty());
        return emailsTo(recipient).get(0);
    }

    private static List<MimeMessage> emailsTo(String recipient) {
        return Arrays.stream(SMTP_SERVER.getReceivedMessages())
                .filter(message -> recipientsOf(message).contains(recipient))
                .toList();
    }

    private static List<String> recipientsOf(MimeMessage message) {
        try {
            Address[] recipients = message.getAllRecipients();
            return recipients == null ? List.of() : Arrays.stream(recipients).map(Address::toString).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String subjectOf(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long nextBookingId() {
        return BOOKING_IDS.incrementAndGet();
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }

    private static String event(String eventId, String type, String email, long bookingId) {
        return """
                {"eventId":"%s","type":"%s","occurredAt":"2026-11-17T10:00:00","bookingId":%d,"userId":7,
                 "userEmail":"%s","userName":"Asha","hotelName":"Sea View Resort","roomType":"Deluxe",
                 "checkInDate":"2026-12-01","checkOutDate":"2026-12-03","roomsCount":2,"amount":4000.00}
                """.formatted(eventId, type, bookingId, email);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int unusedPort() {
        return freePort(); // the socket is closed again, so nothing listens on it
    }
}
