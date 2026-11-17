package com.shardx.hotelManagement.notification.event;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
        assertThat(event.occurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 10, 0));
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
            "{\"eventId\":\"e1\",\"type\":\"\"}", "{\"eventId\":\"e1\",\"type\":\"X\",\"bookingId\":\"abc\"}"})
    void unreadableOrIncompleteMessagesAreMalformed(String payload) {
        assertThatThrownBy(() -> parser.parse(payload)).isInstanceOf(MalformedEventException.class);
    }
}
