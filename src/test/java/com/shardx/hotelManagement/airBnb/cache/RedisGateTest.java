package com.shardx.hotelManagement.airBnb.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RedisGateTest {

    /** A clock the test can move forward. */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;
    private RedisGate gate;
    private AtomicInteger calls;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        gate = new RedisGate(clock);
        calls = new AtomicInteger();
    }

    private String failing() {
        calls.incrementAndGet();
        throw new IllegalStateException("redis is down");
    }

    private String working() {
        calls.incrementAndGet();
        return "value";
    }

    @Test
    void returnsTheResultWhenRedisWorks() {
        assertThat(gate.callOr("get", this::working, "fallback")).isEqualTo("value");
        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void returnsTheFallbackInsteadOfFailingTheRequest() {
        assertThat(gate.callOr("get", this::failing, "fallback")).isEqualTo("fallback");
    }

    @Test
    void opensAfterThreeConsecutiveFailuresAndStopsCallingRedis() {
        for (int i = 0; i < RedisGate.FAILURE_THRESHOLD; i++) {
            gate.callOr("get", this::failing, "fallback");
        }
        assertThat(gate.isOpen()).isTrue();

        int callsBefore = calls.get();
        assertThat(gate.callOr("get", this::working, "fallback")).isEqualTo("fallback");
        assertThat(calls.get()).as("Redis must not be called while the circuit is open").isEqualTo(callsBefore);
    }

    @Test
    void aSuccessInBetweenResetsTheFailureCount() {
        gate.callOr("get", this::failing, "fallback");
        gate.callOr("get", this::failing, "fallback");
        gate.callOr("get", this::working, "fallback");
        gate.callOr("get", this::failing, "fallback");
        gate.callOr("get", this::failing, "fallback");

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void triesAgainAfterTheCoolDownAndClosesWhenRedisIsBack() {
        for (int i = 0; i < RedisGate.FAILURE_THRESHOLD; i++) {
            gate.callOr("get", this::failing, "fallback");
        }
        clock.advance(RedisGate.COOL_DOWN.plusSeconds(1));

        assertThat(gate.isOpen()).isFalse();
        assertThat(gate.callOr("get", this::working, "fallback")).isEqualTo("value");
        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysOpenWhenTheTrialCallAfterTheCoolDownFailsAgain() {
        for (int i = 0; i < RedisGate.FAILURE_THRESHOLD; i++) {
            gate.callOr("get", this::failing, "fallback");
        }
        clock.advance(RedisGate.COOL_DOWN.plusSeconds(1));

        gate.callOr("get", this::failing, "fallback");

        assertThat(gate.isOpen()).isTrue();
    }

    @Test
    void runReportsWhetherTheCallHappened() {
        assertThat(gate.run("set", () -> calls.incrementAndGet())).isTrue();
        assertThat(gate.run("set", () -> {
            throw new IllegalStateException("redis is down");
        })).isFalse();
    }
}
