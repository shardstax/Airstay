package com.shardx.hotelManagement.notification.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Turns a Kafka message into a {@link BookingEvent}, or says it is hopeless ({@link MalformedEventException}).
 *
 * <p>Two stages: first only {@code eventId} and {@code type} are read. An event of a type this service does not send
 * e-mail for is returned as it is (id and type only), so a future event type can never be dead-lettered because of how
 * its other fields look. Only a notifiable event is read in full.
 *
 * <p>Exception messages here never contain parts of the payload (Jackson's normal messages quote a snippet of it, and a
 * payload holds the guest's e-mail address), and the Jackson exception is not kept as the cause, because the container
 * logs the whole cause chain.
 */
@Component
public class EventParser {

    private final ObjectMapper objectMapper;

    public EventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BookingEvent parse(String payload) {
        JsonNode tree;
        try {
            tree = objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new MalformedEventException("Unreadable event: " + e.getOriginalMessage());
        } catch (IllegalArgumentException e) {
            // A null payload (a Kafka tombstone) cannot be read at all.
            throw new MalformedEventException("Unreadable event: there is no message content");
        }
        if (tree == null || !tree.isObject()) {
            throw new MalformedEventException("Unreadable event: the message is not a JSON object");
        }

        String eventId = textOrNull(tree.get("eventId"));
        String type = textOrNull(tree.get("type"));
        if (isBlank(eventId)) {
            throw new MalformedEventException("Event has no eventId");
        }
        if (isBlank(type)) {
            throw new MalformedEventException("Event " + eventId + " has no type");
        }
        if (!EventTypes.isNotifiable(type)) {
            return new BookingEvent(eventId, type, null, null, null, null, null, null, null, null, null, null, null);
        }

        try {
            return objectMapper.treeToValue(tree, BookingEvent.class);
        } catch (JsonProcessingException e) {
            // Only the name of the offending field, never its value (the value could be personal data).
            String where = e instanceof JsonMappingException mapping ? mapping.getPathReference() : "the event";
            throw new MalformedEventException(
                    "Event " + eventId + " (" + type + ") has an unreadable field at " + where);
        }
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
