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
        LocalDateTime now = LocalDateTime.now(clock);
        BookingEvent event = BookingEvent.of(type, booking, now);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + type + " event for booking " + booking.getId(), e);
        }
        repository.save(new OutboxEvent(UUID.fromString(event.eventId()), type.topic(),
                String.valueOf(booking.getId()), type.name(), payload, now));
    }
}
