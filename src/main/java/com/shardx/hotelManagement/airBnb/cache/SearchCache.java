package com.shardx.hotelManagement.airBnb.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Cache-aside for search results in Redis.
 *
 * <p><b>Why this is safe:</b> the cache only holds the read model shown on the search page. Booking never reads it:
 * it row-locks the inventory in PostgreSQL. So a result that is up to one TTL old can at worst list a hotel that then
 * answers 409 when someone tries to book it. It can never cause an overbooking.
 *
 * <p><b>Failure behaviour:</b> every Redis call goes through {@link RedisGate}. If Redis is down or slow, the loader
 * (PostgreSQL) answers and nothing fails.
 *
 * <p><b>Stampede protection:</b> identical concurrent misses on one instance are collapsed into a single loader call
 * (single-flight), and TTLs are jittered so popular entries do not expire at the same instant.
 */
@Component
@Slf4j
public class SearchCache {

    static final String KEY_PREFIX = "search:v1:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisGate gate;
    private final SearchCacheProperties properties;
    private final ConcurrentMap<String, CompletableFuture<CachedSearchPage>> inFlight = new ConcurrentHashMap<>();

    public SearchCache(StringRedisTemplate redis, ObjectMapper objectMapper, RedisGate gate,
                       SearchCacheProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.gate = gate;
        this.properties = properties;
    }

    /** Returns the cached page for the key, or loads it (from PostgreSQL) and caches it. */
    public CachedSearchPage get(String key, Supplier<CachedSearchPage> loader) {
        if (!properties.isEnabled()) {
            return loader.get();
        }
        String redisKey = KEY_PREFIX + key;

        CachedSearchPage cached = read(redisKey);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<CachedSearchPage> mine = new CompletableFuture<>();
        CompletableFuture<CachedSearchPage> leader = inFlight.putIfAbsent(redisKey, mine);
        if (leader != null) {
            // Someone on this instance is already loading the same key: wait for that answer instead of repeating it.
            try {
                return leader.join();
            } catch (CompletionException e) {
                return loader.get(); // the leader failed: do not share its failure, try on our own
            }
        }
        try {
            CachedSearchPage loaded = loader.get();
            mine.complete(loaded);
            write(redisKey, loaded);
            return loaded;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(redisKey, mine);
        }
    }

    private CachedSearchPage read(String redisKey) {
        String json = gate.callOr("search cache get", () -> redis.opsForValue().get(redisKey), null);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CachedSearchPage.class);
        } catch (JsonProcessingException e) {
            log.warn("Ignoring unreadable search cache entry {}: {}", redisKey, e.getOriginalMessage());
            return null; // treated as a miss; the fresh result overwrites it
        }
    }

    private void write(String redisKey, CachedSearchPage page) {
        try {
            String json = objectMapper.writeValueAsString(page);
            Duration ttl = jittered(properties.getTtl(), properties.getTtlJitter());
            gate.run("search cache set", () -> redis.opsForValue().set(redisKey, json, ttl));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise search result for {}: {}", redisKey, e.getOriginalMessage());
        }
    }

    static Duration jittered(Duration ttl, double jitter) {
        double factor = 1 + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
        return Duration.ofMillis(Math.max(1, Math.round(ttl.toMillis() * factor)));
    }
}
