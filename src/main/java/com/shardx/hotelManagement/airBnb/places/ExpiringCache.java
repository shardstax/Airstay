package com.shardx.hotelManagement.airBnb.places;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A small thread-safe cache: entries expire after a fixed time and the least recently used go first once it is full.
 * Per instance (not shared between servers): place data is tiny and barely changes, so each server simply learns what it needs.
 */
final class ExpiringCache<K, V> {

    private record Entry<V>(V value, Instant expiresAt) {
    }

    private final Map<K, Entry<V>> entries;
    private final Duration ttl;
    private final Clock clock;

    ExpiringCache(int maxSize, Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
        // Access order: reading an entry makes it the most recently used.
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
                return size() > maxSize;
            }
        };
    }

    synchronized Optional<V> get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    synchronized void put(K key, V value) {
        entries.put(key, new Entry<>(value, clock.instant().plus(ttl)));
    }

    synchronized int size() {
        return entries.size();
    }
}
