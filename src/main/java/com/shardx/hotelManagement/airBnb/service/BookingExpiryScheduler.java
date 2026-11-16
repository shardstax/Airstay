package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.entity.enums.BookingStatus;
import com.shardx.hotelManagement.airBnb.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Releases rooms held by bookings that were never paid. Each booking is expired in its own
 * transaction (through the BookingService proxy), so one failure does not block the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryScheduler {

    private static final int BATCH_SIZE = 200;

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${airstay.booking.expiry-check-interval-ms}")
    public void releaseExpiredHolds() {
        List<Long> expiredIds = bookingRepository.findExpiredHoldIds(
                BookingStatus.HOLD_STATUSES, LocalDateTime.now(clock), PageRequest.of(0, BATCH_SIZE));
        if (expiredIds.isEmpty()) {
            return;
        }

        int failed = 0;
        for (Long bookingId : expiredIds) {
            try {
                bookingService.expireBooking(bookingId);
            } catch (RuntimeException e) {
                failed++;
                log.error("Could not expire booking {}; will retry on the next run", bookingId, e);
            }
        }
        log.info("Expiry run processed {} lapsed holds ({} failed)", expiredIds.size(), failed);
    }
}
