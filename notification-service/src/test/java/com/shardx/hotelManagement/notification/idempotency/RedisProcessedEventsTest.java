package com.shardx.hotelManagement.notification.idempotency;

import com.shardx.hotelManagement.notification.config.NotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisProcessedEventsTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    private RedisProcessedEvents processedEvents;

    @BeforeEach
    void setUp() {
        processedEvents = new RedisProcessedEvents(redis,
                new NotificationProperties("AirStay <no-reply@airstay.local>", Duration.ofDays(7)));
    }

    @Test
    void anEventIsHandledOnlyWhenItsKeyExists() {
        when(redis.hasKey("notif:evt:seen")).thenReturn(true);
        when(redis.hasKey("notif:evt:new")).thenReturn(false);

        assertThat(processedEvents.wasHandled("seen")).isTrue();
        assertThat(processedEvents.wasHandled("new")).isFalse();
    }

    @Test
    void remembersAnEventForTheConfiguredTime() {
        when(redis.opsForValue()).thenReturn(values);

        processedEvents.markHandled("evt-1");

        verify(values).set("notif:evt:evt-1", "1", Duration.ofDays(7));
    }

    @Test
    void whenRedisIsDownAnEventIsTreatedAsNotHandledSoNoEmailIsLost() {
        when(redis.hasKey("notif:evt:evt-1")).thenThrow(new RedisConnectionFailureException("Redis is down"));

        assertThat(processedEvents.wasHandled("evt-1")).isFalse();
    }

    @Test
    void whenRedisIsDownRememberingAnEventDoesNotFailTheMessage() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis is down"));

        assertThatCode(() -> processedEvents.markHandled("evt-1")).doesNotThrowAnyException();
    }
}
