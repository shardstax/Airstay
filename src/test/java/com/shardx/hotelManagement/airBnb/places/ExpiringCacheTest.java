package com.shardx.hotelManagement.airBnb.places;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiringCacheTest {

    /** A clock the test can move. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-12-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();

    @Test
    void returnsWhatWasStored() {
        ExpiringCache<String, String> cache = new ExpiringCache<>(10, Duration.ofHours(1), clock);

        cache.put("pune", "Pune, Maharashtra, India");

        assertThat(cache.get("pune")).contains("Pune, Maharashtra, India");
        assertThat(cache.get("goa")).isEmpty();
    }

    @Test
    void forgetsAnEntryOnceItsTimeIsUp() {
        ExpiringCache<String, String> cache = new ExpiringCache<>(10, Duration.ofHours(1), clock);
        cache.put("pune", "value");

        clock.advance(Duration.ofMinutes(59));
        assertThat(cache.get("pune")).isPresent();

        clock.advance(Duration.ofMinutes(1));
        assertThat(cache.get("pune")).isEmpty();
        assertThat(cache.size()).as("the expired entry is removed, not just hidden").isZero();
    }

    @Test
    void dropsTheLeastRecentlyUsedEntryWhenFull() {
        ExpiringCache<String, String> cache = new ExpiringCache<>(2, Duration.ofHours(1), clock);
        cache.put("a", "1");
        cache.put("b", "2");

        cache.get("a"); // a is now more recent than b
        cache.put("c", "3");

        assertThat(cache.get("a")).isPresent();
        assertThat(cache.get("b")).as("b was the least recently used").isEmpty();
        assertThat(cache.get("c")).isPresent();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void storingAgainRestartsTheClockForThatEntry() {
        ExpiringCache<String, String> cache = new ExpiringCache<>(10, Duration.ofHours(1), clock);
        cache.put("pune", "old");
        clock.advance(Duration.ofMinutes(50));

        cache.put("pune", "new");
        clock.advance(Duration.ofMinutes(50));

        assertThat(cache.get("pune")).contains("new");
    }
}
