package com.shardx.hotelManagement.airBnb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** One event waiting to be (or already) published to Kafka. See V3__outbox_event.sql for the reasoning. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Null until the relay has published the event. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public OutboxEvent(UUID eventId, String topic, String eventKey, String eventType, String payload,
                       LocalDateTime createdAt) {
        this.eventId = eventId;
        this.topic = topic;
        this.eventKey = eventKey;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }
}
