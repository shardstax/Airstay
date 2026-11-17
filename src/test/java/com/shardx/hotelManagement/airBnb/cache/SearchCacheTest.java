package com.shardx.hotelManagement.airBnb.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardx.hotelManagement.airBnb.dto.HotelPriceResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchCacheTest {

    private static final String KEY = "Pune:2026-12-20:2026-12-22:1:0:10";
    private static final String REDIS_KEY = SearchCache.KEY_PREFIX + KEY;

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SearchCacheProperties properties = new SearchCacheProperties();
    private SearchCache cache;
    private final CachedSearchPage page = new CachedSearchPage(
            List.of(new HotelPriceResponseDto(1L, "Sea View", "Pune", new String[]{"a.jpg"}, new String[]{"WiFi"}, null, 2500.0)),
            1);

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(values);
        cache = new SearchCache(redis, objectMapper, new RedisGate(Clock.systemUTC()), properties);
    }

    private String json(CachedSearchPage value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    void aHitIsReturnedWithoutCallingTheLoader() throws Exception {
        when(values.get(REDIS_KEY)).thenReturn(json(page));
        AtomicInteger loads = new AtomicInteger();

        CachedSearchPage result = cache.get(KEY, () -> {
            loads.incrementAndGet();
            return page;
        });

        assertThat(result).isEqualTo(page);
        assertThat(loads).hasValue(0);
    }

    @Test
    void aMissLoadsFromTheLoaderAndStoresTheResultWithAJitteredTtl() {
        when(values.get(REDIS_KEY)).thenReturn(null);

        CachedSearchPage result = cache.get(KEY, () -> page);

        assertThat(result).isEqualTo(page);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq(REDIS_KEY), anyString(), ttl.capture());
        // 30 s plus or minus 20%
        assertThat(ttl.getValue()).isBetween(Duration.ofSeconds(24), Duration.ofSeconds(36));
    }

    @Test
    void whenRedisIsDownTheLoaderAnswersAndNothingFails() {
        when(values.get(REDIS_KEY)).thenThrow(new RedisConnectionFailureException("redis is down"));

        CachedSearchPage result = cache.get(KEY, () -> page);

        assertThat(result).isEqualTo(page);
    }

    @Test
    void anUnreadableEntryIsTreatedAsAMiss() {
        when(values.get(REDIS_KEY)).thenReturn("this is not json");

        CachedSearchPage result = cache.get(KEY, () -> page);

        assertThat(result).isEqualTo(page);
        verify(values).set(eq(REDIS_KEY), anyString(), any(Duration.class)); // the fresh result overwrites it
    }

    @Test
    void whenDisabledRedisIsNeverTouched() {
        properties.setEnabled(false);

        CachedSearchPage result = cache.get(KEY, () -> page);

        assertThat(result).isEqualTo(page);
        verifyNoInteractions(redis, values);
    }

    @Test
    void aLoaderFailureIsPropagatedAndNothingIsCached() {
        when(values.get(REDIS_KEY)).thenReturn(null);

        assertThatThrownBy(() -> cache.get(KEY, () -> {
            throw new IllegalStateException("database is down");
        })).isInstanceOf(IllegalStateException.class);

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void manyIdenticalConcurrentMissesCauseOnlyOneLoad() throws Exception {
        when(values.get(REDIS_KEY)).thenReturn(null);
        int callers = 20;
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<CachedSearchPage>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return cache.get(KEY, () -> {
                        loads.incrementAndGet();
                        sleep(300); // slow enough that every other caller arrives while this load is running
                        return page;
                    });
                }));
            }
            start.countDown();
            for (Future<CachedSearchPage> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(page);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(loads.get()).as("loader calls for %d concurrent identical requests", callers).isEqualTo(1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
