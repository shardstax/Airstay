package com.shardx.hotelManagement.airBnb.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Every Redis call in AirStay goes through here, so a Redis failure can never fail or stall a request.
 *
 * <ul>
 *   <li><b>Fail open:</b> on any error the caller gets its fallback (for the cache: "miss", so PostgreSQL answers;
 *       for the rate limiter: "allowed").</li>
 *   <li><b>Circuit breaker:</b> after {@value #FAILURE_THRESHOLD} consecutive failures Redis is skipped entirely for
 *       {@link #COOL_DOWN}, then one trial call decides whether to close the circuit again. Without this, every request
 *       would wait for the Redis timeout while it is down.</li>
 *   <li>Failures are logged at most once per {@link #LOG_INTERVAL} so an outage does not flood the log.</li>
 * </ul>
 */
@Component
@Slf4j
public class RedisGate {

    static final int FAILURE_THRESHOLD = 3;
    static final Duration COOL_DOWN = Duration.ofSeconds(10);
    static final Duration LOG_INTERVAL = Duration.ofSeconds(30);

    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntilMillis = new AtomicLong();
    private final AtomicLong lastLogMillis = new AtomicLong();

    public RedisGate(Clock clock) {
        this.clock = clock;
    }

    /** Runs the Redis call, or returns {@code fallback} if it fails or the circuit is open. */
    public <T> T callOr(String operation, Supplier<T> action, T fallback) {
        if (isOpen()) {
            return fallback;
        }
        try {
            T result = action.get();
            consecutiveFailures.set(0);
            openUntilMillis.set(0);
            return result;
        } catch (RuntimeException e) {
            onFailure(operation, e);
            return fallback;
        }
    }

    /** Runs a Redis call whose result does not matter. Returns false if it failed or was skipped. */
    public boolean run(String operation, Runnable action) {
        return callOr(operation, () -> {
            action.run();
            return Boolean.TRUE;
        }, Boolean.FALSE);
    }

    /** True while Redis is being skipped because it keeps failing. */
    public boolean isOpen() {
        long until = openUntilMillis.get();
        return until != 0 && clock.millis() < until;
    }

    private void onFailure(String operation, RuntimeException e) {
        long now = clock.millis();
        if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
            openUntilMillis.set(now + COOL_DOWN.toMillis());
        }
        long last = lastLogMillis.get();
        if (now - last >= LOG_INTERVAL.toMillis() && lastLogMillis.compareAndSet(last, now)) {
            log.warn("Redis {} failed, falling back to PostgreSQL/allow ({} consecutive failures): {}",
                    operation, consecutiveFailures.get(), e.toString());
        }
    }
}
