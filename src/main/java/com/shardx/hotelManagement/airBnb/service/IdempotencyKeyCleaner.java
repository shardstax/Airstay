package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Deletes idempotency keys after the retention period (24 hours by default). A client retrying a request after that
 * long is no longer treated as a duplicate. The bookings themselves are never touched.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyCleaner {

    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    @Value("${airstay.idempotency.retention-hours}")
    private long retentionHours;

    @Scheduled(fixedDelayString = "${airstay.idempotency.cleanup-interval-ms}")
    @Transactional
    public int deleteExpired() {
        int deleted = repository.deleteCreatedBefore(LocalDateTime.now(clock).minus(Duration.ofHours(retentionHours)));
        if (deleted > 0) {
            log.info("Deleted {} expired idempotency keys", deleted);
        }
        return deleted;
    }
}
