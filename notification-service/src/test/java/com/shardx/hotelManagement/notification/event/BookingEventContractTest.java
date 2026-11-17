package com.shardx.hotelManagement.notification.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shardx.hotelManagement.notification.mail.EmailComposer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The event contract is docs/events.md in the repository root. These tests read the example JSON from that very file,
 * so this service can never silently drift away from what the document promises. (Maven runs tests with the module
 * folder as working directory, hence the "..".)
 */
class BookingEventContractTest {

    private static final Path CONTRACT = Path.of("..", "docs", "events.md");
    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void theExampleInTheContractParsesIntoTheEvent() throws IOException {
        BookingEvent event = new EventParser(objectMapper).parse(exampleFromContract());

        assertThat(event.eventId()).isEqualTo("6f1c6f0a-4f6e-4b53-9b1e-2f3d3b7a9c10");
        assertThat(event.type()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(event.bookingId()).isEqualTo(99L);
        assertThat(event.userEmail()).isEqualTo("asha@example.com");
        assertThat(event.userName()).isEqualTo("Asha");
        assertThat(event.hotelName()).isEqualTo("Sea View Resort");
        assertThat(event.roomType()).isEqualTo("Deluxe");
        assertThat(event.checkInDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(event.checkOutDate()).isEqualTo(LocalDate.of(2026, 12, 3));
        assertThat(event.roomsCount()).isEqualTo(2);
        assertThat(event.amount()).isEqualByComparingTo("4000.00");
    }

    @Test
    void everyFieldTheContractDocumentsIsKnownToThisService() throws IOException {
        Set<String> documented = new HashSet<>();
        objectMapper.readTree(exampleFromContract()).fieldNames().forEachRemaining(documented::add);
        Set<String> known = new HashSet<>();
        Arrays.stream(BookingEvent.class.getRecordComponents()).map(RecordComponent::getName).forEach(known::add);

        // paymentSessionId is the one documented field this service deliberately does not read: the contract says it is
        // only ever set on refund requests, never on the public topic.
        Set<String> documentedButUnknown = new HashSet<>(documented);
        documentedButUnknown.removeAll(known);
        assertThat(documentedButUnknown).containsExactly("paymentSessionId");

        Set<String> knownButUndocumented = new HashSet<>(known);
        knownButUndocumented.removeAll(documented);
        assertThat(knownButUndocumented).isEmpty();
    }

    @Test
    void theExampleInTheContractProducesAConfirmationEmail() throws IOException {
        BookingEvent event = new EventParser(objectMapper).parse(exampleFromContract());

        var email = new EmailComposer().compose(event);

        assertThat(email).isPresent();
        assertThat(email.get().to()).isEqualTo("asha@example.com");
        assertThat(email.get().subject()).contains("#99").contains("confirmed");
    }

    private static String exampleFromContract() throws IOException {
        assertThat(CONTRACT).as("the contract document must be found at %s (tests run from notification-service/)",
                CONTRACT.toAbsolutePath().normalize()).exists();
        Matcher matcher = JSON_BLOCK.matcher(Files.readString(CONTRACT));
        assertThat(matcher.find()).as("docs/events.md must contain a ```json example").isTrue();
        JsonNode check = null;
        try {
            check = JsonMapper.builder().build().readTree(matcher.group(1));
        } catch (IOException e) {
            throw new AssertionError("The JSON example in docs/events.md is not valid JSON", e);
        }
        assertThat(check.isObject()).isTrue();
        return matcher.group(1);
    }
}
