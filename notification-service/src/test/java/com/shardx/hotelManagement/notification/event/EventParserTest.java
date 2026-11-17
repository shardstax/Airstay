package com.shardx.hotelManagement.notification.event;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventParserTest {

    private final EventParser parser = new EventParser(JsonMapper.builder().findAndAddModules().build());

    @Test
    void readsAValidEvent() {
        BookingEvent event = parser.parse("""
                {"eventId":"e1","type":"BOOKING_CANCELLED","occurredAt":"2026-11-17T10:00:00","bookingId":5,
                 "userId":2,"userEmail":"a@b.test","userName":"A","hotelName":"H","roomType":"R",
                 "checkInDate":"2026-12-01","checkOutDate":"2026-12-03","roomsCount":1,"amount":100.5}
                """);

        assertThat(event.eventId()).isEqualTo("e1");
        assertThat(event.checkOutDate()).isEqualTo(LocalDate.of(2026, 12, 3));
        assertThat(event.amount()).isEqualByComparingTo("100.5");
    }

    @Test
    void ignoresFieldsAddedByALaterVersionOfTheApi() {
        BookingEvent event = parser.parse("""
                {"eventId":"e1","type":"BOOKING_CONFIRMED","currency":"INR","somethingNew":{"a":[1,2]}}
                """);

        assertThat(event.eventId()).isEqualTo("e1");
    }

    @Test
    void anUnknownTypeStillParsesSoTheListenerCanIgnoreIt() {
        assertThat(parser.parse("{\"eventId\":\"e1\",\"type\":\"HOTEL_RENAMED\"}").type()).isEqualTo("HOTEL_RENAMED");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "not json", "{broken", "null", "[1,2,3]", "{\"type\":\"BOOKING_CONFIRMED\"}",
            "{\"eventId\":\"e1\"}", "{\"eventId\":\" \",\"type\":\"BOOKING_CONFIRMED\"}",
            "{\"eventId\":\"e1\",\"type\":\"\"}", "{\"eventId\":123,\"type\":\"BOOKING_CONFIRMED\"}",
            "{\"eventId\":\"e1\",\"type\":\"BOOKING_CONFIRMED\",\"bookingId\":\"abc\"}"})
    void unreadableOrIncompleteMessagesAreMalformed(String payload) {
        assertThatThrownBy(() -> parser.parse(payload)).isInstanceOf(MalformedEventException.class);
    }

    @Test
    void anUnknownTypeIsIgnoredEvenWhenItsOtherFieldsCannotBeRead() {
        // A future event type may use any shape for its other fields; it must never be dead-lettered because of them.
        BookingEvent event = parser.parse("""
                {"eventId":"e1","type":"HOTEL_RENAMED","bookingId":"not a number","checkInDate":{"year":2027},
                 "amount":[1,2],"roomsCount":"many"}
                """);

        assertThat(event.eventId()).isEqualTo("e1");
        assertThat(event.type()).isEqualTo("HOTEL_RENAMED");
    }

    @Test
    void aTimestampInAnotherFormatDoesNotBreakAnEvent() {
        // occurredAt is not used by this service, so a change in its format (here: with a time zone) must not matter.
        BookingEvent event = parser.parse("""
                {"eventId":"e1","type":"BOOKING_CONFIRMED","occurredAt":"2026-11-17T10:00:00+05:30","bookingId":5,
                 "userEmail":"a@b.test","hotelName":"H","roomType":"R","checkInDate":"2026-12-01",
                 "checkOutDate":"2026-12-03","roomsCount":1,"amount":10}
                """);

        assertThat(event.bookingId()).isEqualTo(5L);
    }

    @Test
    void errorMessagesNeverContainPartsOfThePayloadBecauseItHoldsAnEmailAddress() {
        String secret = "secret.guest@example.test";
        String truncated = "{\"eventId\":\"e1\",\"type\":\"BOOKING_CONFIRMED\",\"userEmail\":\"" + secret + "\",\"bookingId\"";
        String badField = "{\"eventId\":\"e1\",\"type\":\"BOOKING_CONFIRMED\",\"userEmail\":\"" + secret
                + "\",\"checkInDate\":\"not-a-date-" + secret + "\"}";

        for (String payload : new String[]{truncated, badField}) {
            assertThatThrownBy(() -> parser.parse(payload))
                    .isInstanceOfSatisfying(MalformedEventException.class, e -> {
                        assertThat(e.getMessage()).doesNotContain(secret).doesNotContain("secret");
                        // The container logs the whole cause chain, so no Jackson exception may be kept as the cause.
                        assertThat(e.getCause()).isNull();
                    });
        }
    }
}
