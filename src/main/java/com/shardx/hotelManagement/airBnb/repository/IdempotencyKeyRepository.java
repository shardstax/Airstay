package com.shardx.hotelManagement.airBnb.repository;

import com.shardx.hotelManagement.airBnb.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    /**
     * Claims the key for this user. Returns 1 if the key was new, 0 if it already existed.
     *
     * <p>If another transaction is inserting the same key right now (not yet committed), PostgreSQL makes this
     * statement WAIT until that transaction finishes: if it committed the result is 0 (replay), if it rolled back the
     * insert succeeds and this request proceeds. That is what makes simultaneous retries safe.
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_key (user_id, idempotency_key, request_hash, created_at)
            VALUES (:userId, :key, :requestHash, :createdAt)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("userId") Long userId,
              @Param("key") String key,
              @Param("requestHash") String requestHash,
              @Param("createdAt") LocalDateTime createdAt);

    Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
