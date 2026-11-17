package com.shardx.hotelManagement.notification.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Turns a Kafka message into a {@link BookingEvent}, or says it is hopeless ({@link MalformedEventException}). */
@Component
public class EventParser {

    private final ObjectMapper objectMapper;

    public EventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Every event needs an id (to ignore duplicates) and a type (to know what it is). Whether the rest is complete
     * depends on the type, so that check lives where the fields are used.
     */
    public BookingEvent parse(String payload) {
        BookingEvent event;
        try {
            event = objectMapper.readValue(payload, BookingEvent.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            // IllegalArgumentException: a null payload (Kafka tombstone) cannot be read at all.
            throw new MalformedEventException("Unreadable event: " + e.getMessage(), e);
        }
        if (event == null) {
            throw new MalformedEventException("Unreadable event: the message is JSON null");
        }
        if (isBlank(event.eventId())) {
            throw new MalformedEventException("Event has no eventId");
        }
        if (isBlank(event.type())) {
            throw new MalformedEventException("Event " + event.eventId() + " has no type");
        }
        return event;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
