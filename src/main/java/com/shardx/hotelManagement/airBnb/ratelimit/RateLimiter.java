package com.shardx.hotelManagement.airBnb.ratelimit;

import com.shardx.hotelManagement.airBnb.cache.RedisGate;
import com.shardx.hotelManagement.airBnb.exception.RateLimitExceededException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fixed-window rate limiter on Redis counters. Every app instance talks to the same Redis, so a limit of 10 per minute
 * means 10 per minute in total, not 10 per instance.
 *
 * <p>The count and its expiry are set by one Lua script, which Redis runs atomically, so concurrent requests cannot
 * slip past the limit and a counter can never be left without an expiry. The window starts at the first request.
 * Known trade-off of fixed windows: up to twice the limit can pass across a window boundary. That is fine for abuse
 * protection and keeps it to one Redis round trip.
 *
 * <p>Fails open: if Redis is down the request is allowed (availability over strictness) and {@link RedisGate} stops
 * the failing calls from slowing requests.
 */
@Component
public class RateLimiter {

    static final String KEY_PREFIX = "rl:";
    private static final int MAX_SUBJECT_LENGTH = 200;

    // Returns { count, remaining time-to-live in ms }. PTTL < 0 would mean "no expiry": repair it so a key can never live forever.
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> COUNT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if count == 1 or ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    private final StringRedisTemplate redis;
    private final RedisGate gate;
    private final RateLimitProperties properties;

    public RateLimiter(StringRedisTemplate redis, RedisGate gate, RateLimitProperties properties) {
        this.redis = redis;
        this.gate = gate;
        this.properties = properties;
    }

    /**
     * Counts one request for {@code subject} under the named rule.
     *
     * @throws RateLimitExceededException when the subject is over the limit
     */
    public void check(String name, String subject, RateLimitProperties.Rule rule) {
        if (!properties.isEnabled()) {
            return;
        }
        checkDecision(gate.callOr("rate limit", () -> evaluate(key(name, subject), rule), Decision.ALLOWED));
    }

    /** Paid requests must be counted even when ordinary, fail-open limits are disabled. */
    public boolean checkRequired(String name, String subject, RateLimitProperties.Rule rule) {
        Decision decision = gate.callOr("required rate limit", () -> evaluate(key(name, subject), rule), null);
        if (decision == null) return false;
        checkDecision(decision);
        return true;
    }

    private String key(String name, String subject) {
        return KEY_PREFIX + name + ":" + (subject.length() > MAX_SUBJECT_LENGTH
                ? subject.substring(0, MAX_SUBJECT_LENGTH) : subject);
    }

    private void checkDecision(Decision decision) {
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }

    private Decision evaluate(String key, RateLimitProperties.Rule rule) {
        @SuppressWarnings("rawtypes")
        List result = redis.execute(COUNT_SCRIPT, List.of(key), Long.toString(rule.getWindow().toMillis()));
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Unexpected rate limit script result: " + result);
        }
        long count = ((Number) result.get(0)).longValue();
        long ttlMillis = ((Number) result.get(1)).longValue();
        if (count <= rule.getLimit()) {
            return Decision.ALLOWED;
        }
        return new Decision(false, Math.max(1, (ttlMillis + 999) / 1000)); // round up to whole seconds
    }

    private record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0);
    }
}
