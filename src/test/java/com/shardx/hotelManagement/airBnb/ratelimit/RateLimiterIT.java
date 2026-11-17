package com.shardx.hotelManagement.airBnb.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.cache.RedisGate;
import com.shardx.hotelManagement.airBnb.exception.RateLimitExceededException;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rate limiting against a real Redis, over HTTP and directly. Rules are tightened per test and restored afterwards. */
class RateLimiterIT extends IntegrationTestBase {

    @Autowired
    private RateLimiter limiter;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private StringRedisTemplate redis;

    private RateLimitProperties.Rule originalLoginPerEmail;
    private RateLimitProperties.Rule originalBookingInit;
    private RestTemplate plain;

    @BeforeEach
    void rememberRules() {
        plain = newPlainClient(); // never retries a 429 by itself, unlike the shared TestRestTemplate
        originalLoginPerEmail = new RateLimitProperties.Rule(properties.getLoginPerEmail().getLimit(),
                properties.getLoginPerEmail().getWindow());
        originalBookingInit = new RateLimitProperties.Rule(properties.getBookingInitPerUser().getLimit(),
                properties.getBookingInitPerUser().getWindow());
    }

    @AfterEach
    void restoreRules() {
        properties.setLoginPerEmail(originalLoginPerEmail);
        properties.setBookingInitPerUser(originalBookingInit);
        properties.setEnabled(true);
    }

    @Test
    void loginIsLimitedPerEmailAndTheAnswerSaysWhenToRetry() {
        properties.setLoginPerEmail(new RateLimitProperties.Rule(3, Duration.ofMinutes(1)));
        String email = "limited-" + UUID.randomUUID() + "@example.com";
        post("/auth/signup", null, Map.of("email", email, "password", PASSWORD, "name", "Limited"));

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(login(email).getStatusCode()).as("attempt %d", attempt).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<JsonNode> blocked = login(email);

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(error(blocked).path("status").asText()).isEqualTo("TOO_MANY_REQUESTS");
        long retryAfter = Long.parseLong(blocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).as("Retry-After seconds").isBetween(1L, 60L);

        // Someone else's login is not affected by this account's counter.
        String other = "other-" + UUID.randomUUID() + "@example.com";
        post("/auth/signup", null, Map.of("email", other, "password", PASSWORD, "name", "Other"));
        assertThat(login(other).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void guessingWrongPasswordsIsStoppedByTheSameLimit() {
        properties.setLoginPerEmail(new RateLimitProperties.Rule(3, Duration.ofMinutes(1)));
        String email = "victim-" + UUID.randomUUID() + "@example.com";
        post("/auth/signup", null, Map.of("email", email, "password", PASSWORD, "name", "Victim"));

        for (int guess = 1; guess <= 3; guess++) {
            assertThat(post("/auth/login", null, Map.of("email", email, "password", "wrong-" + guess))
                    .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        // The fourth attempt is refused before the password is even checked, even if it is the right one.
        assertThat(login(email).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void bookingAttemptsAreLimitedPerUserBeforeTheyReachTheInventory() {
        properties.setBookingInitPerUser(new RateLimitProperties.Rule(2, Duration.ofMinutes(1)));
        TestHotel hotel = createActiveHotel(newManagerToken(), 5, "1000.00");
        String guest = newGuestToken();
        LocalDate day = LocalDate.now().plusDays(40);

        assertThat(initBooking(guest, hotel, day, day.plusDays(1), 1).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(initBooking(guest, hotel, day.plusDays(5), day.plusDays(6), 1).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<JsonNode> blocked = plainPost(plain, "/bookings/init", guest, Map.of(
                "hotelId", hotel.hotelId(), "roomId", hotel.roomId(), "checkInDate", day.plusDays(10).toString(),
                "checkOutDate", day.plusDays(11).toString(), "roomsCount", 1));

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(reservedCount(hotel.roomId(), day.plusDays(10))).as("blocked request reserved nothing").isZero();
        // Another user can still book.
        assertThat(initBooking(newGuestToken(), hotel, day.plusDays(10), day.plusDays(11), 1).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void theLimitIsSharedBetweenTwoApplicationInstances() {
        // A second connection factory stands in for a second app instance: its own connections, the same Redis.
        LettuceConnectionFactory secondConnection = new LettuceConnectionFactory(
                ((LettuceConnectionFactory) redis.getConnectionFactory()).getStandaloneConfiguration());
        secondConnection.afterPropertiesSet();
        try {
            RateLimiter instanceA = limiter;
            RateLimiter instanceB = new RateLimiter(new StringRedisTemplate(secondConnection),
                    new RedisGate(Clock.systemUTC()), properties);
            RateLimitProperties.Rule rule = new RateLimitProperties.Rule(5, Duration.ofMinutes(1));
            String subject = "shared-" + UUID.randomUUID();

            for (int i = 0; i < 3; i++) {
                instanceA.check("shared-test", subject, rule);
            }
            for (int i = 0; i < 2; i++) {
                instanceB.check("shared-test", subject, rule);
            }

            // 3 + 2 = 5 requests used up the limit across both instances, so neither may take a sixth.
            assertThatThrownBy(() -> instanceA.check("shared-test", subject, rule))
                    .isInstanceOf(RateLimitExceededException.class);
            assertThatThrownBy(() -> instanceB.check("shared-test", subject, rule))
                    .isInstanceOf(RateLimitExceededException.class);
        } finally {
            secondConnection.destroy();
        }
    }

    @Test
    void exactlyTheLimitPassesWhenManyRequestsArriveAtTheSameInstant() throws Exception {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule(10, Duration.ofMinutes(1));
        String subject = "burst-" + UUID.randomUUID();
        int threads = 50;
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        limiter.check("burst-test", subject, rule);
                        allowed.incrementAndGet();
                    } catch (RateLimitExceededException expected) {
                        // over the limit
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(allowed.get()).as("requests allowed out of %d simultaneous", threads).isEqualTo(10);
    }

    @Test
    void theCounterStartsAgainWhenTheWindowEnds() throws Exception {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule(2, Duration.ofSeconds(1));
        String subject = "window-" + UUID.randomUUID();

        limiter.check("window-test", subject, rule);
        limiter.check("window-test", subject, rule);
        assertThatThrownBy(() -> limiter.check("window-test", subject, rule))
                .isInstanceOf(RateLimitExceededException.class);

        Thread.sleep(1300);

        limiter.check("window-test", subject, rule); // a new window: allowed again
    }

    @Test
    void everyCounterGetsAnExpirySoRedisNeverFillsUp() {
        String subject = "expiry-" + UUID.randomUUID();
        limiter.check("expiry-test", subject, new RateLimitProperties.Rule(5, Duration.ofMinutes(1)));

        Long ttlSeconds = redis.getExpire(RateLimiter.KEY_PREFIX + "expiry-test:" + subject);

        assertThat(ttlSeconds).isBetween(1L, 60L);
    }

    private ResponseEntity<JsonNode> login(String email) {
        return plainPost(plain, "/auth/login", null, Map.of("email", email, "password", PASSWORD));
    }
}
