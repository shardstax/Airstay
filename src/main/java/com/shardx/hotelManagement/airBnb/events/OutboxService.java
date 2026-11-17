package com.shardx.hotelManagement.airBnb.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardx.hotelManagement.airBnb.entity.Booking;
import com.shardx.hotelManagement.airBnb.entity.OutboxEvent;
import com.shardx.hotelManagement.airBnb.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Saves events for Kafka in the booking's own database transaction (the transactional outbox pattern).
 *
 * <p>{@code MANDATORY} is deliberate: calling this outside a transaction fails immediately. An event saved in a
 * transaction of its own could outlive a business change that was rolled back, or the reverse, and the whole point of
 * the outbox is that the two always commit or roll back together.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(BookingEventType type, Booking booking) {
        save(type, booking, UUID.randomUUID());
    }

    /**
     * Records the event with a chosen id, unless an event with that id already exists. Used when the event is derived
     * from something that can be delivered twice (a Kafka message): the second delivery finds the first one's event and
     * does nothing, so consumers never see a duplicate.
     *
     * @return true if the event was recorded now, false if it already existed
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordOnce(BookingEventType type, Booking booking, UUID eventId) {
        if (repository.existsByEventId(eventId)) {
            return false;
        }
        save(type, booking, eventId);
        return true;
    }

    private void save(BookingEventType type, Booking booking, UUID eventId) {
        LocalDateTime now = LocalDateTime.now(clock);
        BookingEvent event = BookingEvent.of(type, booking, now, eventId);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + type + " event for booking " + booking.getId(), e);
        }
        repository.save(new OutboxEvent(eventId, type.topic(), String.valueOf(booking.getId()), type.name(), payload, now));
    }
}
