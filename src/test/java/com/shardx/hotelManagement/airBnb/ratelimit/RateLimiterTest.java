package com.shardx.hotelManagement.airBnb.ratelimit;

import com.shardx.hotelManagement.airBnb.cache.RedisGate;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class RateLimiterTest {

    private final RateLimitProperties properties = new RateLimitProperties();
    private final RateLimitProperties.Rule rule = new RateLimitProperties.Rule(1, Duration.ofMinutes(1));

    @Test
    void whenRedisIsDownEveryRequestIsAllowed() {
        // Every method on this template fails like a dead Redis connection would.
        StringRedisTemplate deadRedis = mock(StringRedisTemplate.class, invocation -> {
            throw new RedisConnectionFailureException("redis is down");
        });
        RateLimiter limiter = new RateLimiter(deadRedis, new RedisGate(Clock.systemUTC()), properties);

        assertThatCode(() -> {
            for (int i = 0; i < 20; i++) { // far over the limit of 1, still never rejected
                limiter.check("login-email", "someone@example.com", rule);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void requiredQuotaStopsPaidWorkWhenRedisCannotCountIt() {
        properties.setEnabled(false);
        StringRedisTemplate redis = mock(StringRedisTemplate.class, invocation -> {
            throw new RedisConnectionFailureException("redis is down");
        });
        RateLimiter limiter = new RateLimiter(redis, new RedisGate(Clock.systemUTC()), properties);

        assertThat(limiter.checkRequired("ai-search-global", "all", rule)).isFalse();
    }

    @Test
    void whenDisabledRedisIsNeverCalled() {
        properties.setEnabled(false);
        StringRedisTemplate redis = mock(StringRedisTemplate.class, invocation -> {
            throw new AssertionError("Redis must not be called when rate limiting is disabled");
        });
        RateLimiter limiter = new RateLimiter(redis, new RedisGate(Clock.systemUTC()), properties);

        assertThatCode(() -> limiter.check("login-email", "someone@example.com", rule)).doesNotThrowAnyException();
    }
}
