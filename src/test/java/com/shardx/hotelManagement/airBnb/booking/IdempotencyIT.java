package com.shardx.hotelManagement.airBnb.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.service.IdempotencyKeyCleaner;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Idempotency-Key on POST /bookings/init against a real PostgreSQL. */
class IdempotencyIT extends IntegrationTestBase {

    private static final String REPLAYED = "Idempotent-Replayed";

    @Autowired
    private IdempotencyKeyCleaner cleaner;

    private final LocalDate checkIn = LocalDate.now().plusDays(70);
    private final LocalDate checkOut = checkIn.plusDays(2);

    @Test
    void repeatingARequestWithTheSameKeyReturnsTheSameBookingAndHoldsRoomsOnlyOnce() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 3, "1000.00"); // 3 rooms: a duplicate would show up
        String guest = newGuestToken();
        String key = newKey();

        ResponseEntity<JsonNode> first = initWithKey(guest, hotel, checkIn, checkOut, 1, key);
        ResponseEntity<JsonNode> retry = initWithKey(guest, hotel, checkIn, checkOut, 1, key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getHeaders().containsKey(REPLAYED)).as("first request is not a replay").isFalse();
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getHeaders().getFirst(REPLAYED)).isEqualTo("true");
        assertThat(data(retry).path("id").asLong()).isEqualTo(data(first).path("id").asLong());
        assertThat(bookingsForRoom(hotel.roomId())).isEqualTo(1);
        assertThat(reservedCount(hotel.roomId(), checkIn)).as("rooms held after the retry").isEqualTo(1);
    }

    @Test
    void withoutAKeyEveryRequestBooksAgain() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 3, "1000.00");
        String guest = newGuestToken();

        initWithKey(guest, hotel, checkIn, checkOut, 1, null);
        initWithKey(guest, hotel, checkIn, checkOut, 1, null);

        assertThat(bookingsForRoom(hotel.roomId())).isEqualTo(2);
        assertThat(reservedCount(hotel.roomId(), checkIn)).isEqualTo(2);
    }

    @Test
    void theSameKeyForADifferentRequestIsRefusedAndHoldsNothing() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 3, "1000.00");
        String guest = newGuestToken();
        String key = newKey();
        initWithKey(guest, hotel, checkIn, checkOut, 1, key);

        ResponseEntity<JsonNode> other = initWithKey(guest, hotel, checkIn, checkOut, 2, key); // 2 rooms, not 1

        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(error(other).path("message").asText()).contains("different request");
        assertThat(reservedCount(hotel.roomId(), checkIn)).as("nothing extra held").isEqualTo(1);
    }

    @Test
    void tenSimultaneousRetriesWithOneKeyCreateExactlyOneBooking() throws Exception {
        TestHotel hotel = createActiveHotel(newManagerToken(), 5, "1000.00");
        String guest = newGuestToken();
        String key = newKey();
        int retries = 10;
        RestTemplate racingClient = newPlainClient();

        List<ResponseEntity<JsonNode>> responses = runAtTheSameTime(java.util.stream.IntStream.range(0, retries)
                .<Callable<ResponseEntity<JsonNode>>>mapToObj(i -> () ->
                        postInit(racingClient, guest, hotel, checkIn, checkOut, 1, key))
                .toList());

        Set<Long> bookingIds = new HashSet<>();
        long newOnes = 0;
        for (ResponseEntity<JsonNode> response : responses) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            bookingIds.add(response.getBody().path("data").path("id").asLong());
            if (!response.getHeaders().containsKey(REPLAYED)) {
                newOnes++;
            }
        }
        assertThat(bookingIds).as("every retry got the same booking").hasSize(1);
        assertThat(newOnes).as("exactly one request actually created it").isEqualTo(1);
        assertThat(bookingsForRoom(hotel.roomId())).isEqualTo(1);
        assertThat(reservedCount(hotel.roomId(), checkIn)).isEqualTo(1);
    }

    @Test
    void aFailedAttemptDoesNotUseUpTheKey() {
        String manager = newManagerToken();
        TestHotel hotel = createActiveHotel(manager, 1, "1000.00");
        String guest = newGuestToken();
        String key = newKey();

        setClosed(manager, hotel, true);
        ResponseEntity<JsonNode> failed = initWithKey(guest, hotel, checkIn, checkOut, 1, key);
        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(idempotencyKeyRows(key)).as("the failed attempt left no key behind").isZero();

        setClosed(manager, hotel, false);
        ResponseEntity<JsonNode> retry = initWithKey(guest, hotel, checkIn, checkOut, 1, key);

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getHeaders().containsKey(REPLAYED)).as("a real booking, not a replay").isFalse();
    }

    @Test
    void keysAreScopedPerUser() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 3, "1000.00");
        String key = newKey();

        ResponseEntity<JsonNode> first = initWithKey(newGuestToken(), hotel, checkIn, checkOut, 1, key);
        ResponseEntity<JsonNode> second = initWithKey(newGuestToken(), hotel, checkIn, checkOut, 1, key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(second).path("id").asLong()).isNotEqualTo(data(first).path("id").asLong());
        assertThat(second.getHeaders().containsKey(REPLAYED)).isFalse();
    }

    @Test
    void anInvalidKeyIsRejectedBeforeAnythingHappens() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 3, "1000.00");
        String guest = newGuestToken();

        ResponseEntity<JsonNode> tooShort = initWithKey(guest, hotel, checkIn, checkOut, 1, "short");
        ResponseEntity<JsonNode> badCharacters = initWithKey(guest, hotel, checkIn, checkOut, 1, "has spaces in it!");

        assertThat(tooShort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badCharacters.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reservedCount(hotel.roomId(), checkIn)).isZero();
    }

    @Test
    void theCleanupJobDeletesOnlyKeysOlderThanTheRetentionPeriod() {
        String email = "keys-" + UUID.randomUUID() + "@example.com";
        post("/auth/signup", null, Map.of("email", email, "password", PASSWORD, "name", "Keys"));
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, email);
        String oldKey = newKey();
        String freshKey = newKey();
        jdbc.update("INSERT INTO idempotency_key (user_id, idempotency_key, request_hash, created_at) VALUES (?, ?, 'h', ?)",
                userId, oldKey, LocalDateTime.now().minusHours(25));
        jdbc.update("INSERT INTO idempotency_key (user_id, idempotency_key, request_hash, created_at) VALUES (?, ?, 'h', ?)",
                userId, freshKey, LocalDateTime.now().minusHours(1));

        int deleted = cleaner.deleteExpired();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(idempotencyKeyRows(oldKey)).as("25 hours old: deleted").isZero();
        assertThat(idempotencyKeyRows(freshKey)).as("1 hour old: kept").isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------------

    private static String newKey() {
        return "test-key-" + UUID.randomUUID();
    }

    private int bookingsForRoom(long roomId) {
        return jdbc.queryForObject("SELECT count(*) FROM booking WHERE room_id = ?", Integer.class, roomId);
    }

    private int idempotencyKeyRows(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM idempotency_key WHERE idempotency_key = ?", Integer.class, key);
    }

    private ResponseEntity<JsonNode> initWithKey(String token, TestHotel hotel, LocalDate in, LocalDate out,
                                                 int rooms, String key) {
        return postInit(rest, token, hotel, in, out, rooms, key);
    }

    private ResponseEntity<JsonNode> postInit(Object client, String token, TestHotel hotel, LocalDate in, LocalDate out,
                                          int rooms, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        Map<String, Object> body = Map.of("hotelId", hotel.hotelId(), "roomId", hotel.roomId(),
                "checkInDate", in.toString(), "checkOutDate", out.toString(), "roomsCount", rooms);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        if (client instanceof RestTemplate racing) {
            return racing.exchange("/bookings/init", HttpMethod.POST, request, JsonNode.class);
        }
        return rest.exchange("/bookings/init", HttpMethod.POST, request, JsonNode.class);
    }

    private void setClosed(String managerToken, TestHotel hotel, boolean closed) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(managerToken);
        Map<String, Object> body = Map.of("startDate", checkIn.toString(), "endDate", checkOut.toString(),
                "surgeFactor", 1.0, "closed", closed);
        ResponseEntity<JsonNode> response = rest.exchange("/admin/inventory/rooms/" + hotel.roomId(),
                HttpMethod.PATCH, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }


    private static <T> List<T> runAtTheSameTime(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
