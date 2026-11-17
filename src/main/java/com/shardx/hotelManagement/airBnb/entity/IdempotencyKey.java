package com.shardx.hotelManagement.airBnb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Remembers that a user already sent a booking request with this key, and which booking it created.
 * Rows are only inserted with {@code IdempotencyKeyRepository#claim}; the entity is used to read them and to link the
 * booking once it exists.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "idempotency_key", uniqueConstraints = @UniqueConstraint(
        name = "unique_user_idempotency_key", columnNames = {"user_id", "idempotency_key"}))
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    /** SHA-256 of the request: the same key may only ever repeat the same request. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
