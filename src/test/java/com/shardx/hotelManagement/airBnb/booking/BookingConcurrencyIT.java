package com.shardx.hotelManagement.airBnb.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fires many booking requests at the same instant through the real HTTP stack and PostgreSQL row
 * locks, then checks the inventory table: no night may ever be sold beyond its room count.
 */
class BookingConcurrencyIT extends IntegrationTestBase {

    private static final String SOLD_OUT_MESSAGE = "Room is not available for the selected dates";

    /**
     * The shared TestRestTemplate pools at most 5 connections per host, which would quietly queue most
     * of the "simultaneous" requests. This client opens a connection per request, so they really race.
     */
    private RestTemplate racingClient;

    @BeforeEach
    void createRacingClient() {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        racingClient = new RestTemplate(new JdkClientHttpRequestFactory(httpClient));
        racingClient.setUriTemplateHandler(new DefaultUriBuilderFactory(rest.getRootUri()));
        racingClient.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false; // 409s are expected answers here, not exceptions
            }
        });
    }

    @Test
    void exactlyOneOfManySimultaneousRequestsGetsTheLastRoom() throws Exception {
        int requests = 25;
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");
        String guest = newGuestToken();
        LocalDate checkIn = LocalDate.now().plusDays(30);
        LocalDate checkOut = checkIn.plusDays(2);

        List<ResponseEntity<JsonNode>> responses = runAtTheSameTime(IntStream.range(0, requests)
                .<Callable<ResponseEntity<JsonNode>>>mapToObj(i -> () -> racingInitBooking(guest, hotel, checkIn, checkOut))
                .toList());

        assertThat(responses).filteredOn(r -> r.getStatusCode().value() == HttpStatus.CREATED.value()).hasSize(1);
        assertThat(responses).filteredOn(r -> r.getStatusCode().value() == HttpStatus.CONFLICT.value())
                .hasSize(requests - 1)
                .allSatisfy(r -> assertThat(error(r).path("message").asText()).isEqualTo(SOLD_OUT_MESSAGE));
        assertThat(reservedCount(hotel.roomId(), checkIn)).isEqualTo(1);
        assertThat(reservedCount(hotel.roomId(), checkIn.plusDays(1))).isEqualTo(1);
    }

    @Test
    void overlappingStaysNeverExceedCapacityOnAnyNight() throws Exception {
        int capacity = 3;
        int requests = 30;
        TestHotel hotel = createActiveHotel(newManagerToken(), capacity, "1000.00");
        String guest = newGuestToken();
        LocalDate windowStart = LocalDate.now().plusDays(40);

        // Stays of 1-3 nights starting on 5 different days, so they overlap in many combinations.
        record Stay(LocalDate checkIn, LocalDate checkOut) {
            boolean covers(LocalDate night) {
                return !night.isBefore(checkIn) && night.isBefore(checkOut);
            }
        }
        List<Stay> stays = IntStream.range(0, requests)
                .mapToObj(i -> {
                    LocalDate in = windowStart.plusDays(i % 5);
                    return new Stay(in, in.plusDays(1 + i % 3));
                })
                .toList();

        List<ResponseEntity<JsonNode>> responses = runAtTheSameTime(stays.stream()
                .<Callable<ResponseEntity<JsonNode>>>map(stay -> () ->
                        racingInitBooking(guest, hotel, stay.checkIn(), stay.checkOut()))
                .toList());

        List<Stay> accepted = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            HttpStatus status = HttpStatus.valueOf(responses.get(i).getStatusCode().value());
            assertThat(status).as("request %d", i).isIn(HttpStatus.CREATED, HttpStatus.CONFLICT);
            if (status == HttpStatus.CREATED) {
                accepted.add(stays.get(i));
            } else {
                // A sold-out answer, never a deadlock or lock timeout in disguise
                assertThat(error(responses.get(i)).path("message").asText()).isEqualTo(SOLD_OUT_MESSAGE);
            }
        }
        assertThat(accepted).isNotEmpty();

        // Every night holds exactly the accepted stays that cover it, and never more than capacity.
        for (int day = 0; day < 8; day++) {
            LocalDate night = windowStart.plusDays(day);
            long expected = accepted.stream().filter(stay -> stay.covers(night)).count();
            assertThat(reservedCount(hotel.roomId(), night)).as("rooms held on %s", night)
                    .isEqualTo((int) expected)
                    .isLessThanOrEqualTo(capacity);
        }
    }

    private ResponseEntity<JsonNode> racingInitBooking(String token, TestHotel hotel, LocalDate checkIn, LocalDate checkOut) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        Map<String, Object> body = Map.of(
                "hotelId", hotel.hotelId(),
                "roomId", hotel.roomId(),
                "checkInDate", checkIn.toString(),
                "checkOutDate", checkOut.toString(),
                "roomsCount", 1);
        return racingClient.exchange("/bookings/init", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    /** Starts every task at once (behind a latch) and returns the results in task order. */
    private static <T> List<T> runAtTheSameTime(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch startSignal = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    return task.call();
                }));
            }
            startSignal.countDown();

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
