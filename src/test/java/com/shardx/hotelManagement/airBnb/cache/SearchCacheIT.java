package com.shardx.hotelManagement.airBnb.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The search cache against a real Redis and PostgreSQL. The cache is off by default in tests; each test turns it on. */
class SearchCacheIT extends IntegrationTestBase {

    @Autowired
    private SearchCacheProperties properties;

    @Autowired
    private StringRedisTemplate redis;

    private final LocalDate checkIn = LocalDate.now().plusDays(20);
    private final LocalDate checkOut = checkIn.plusDays(2);

    @BeforeEach
    void enableCache() {
        properties.setEnabled(true);
    }

    @AfterEach
    void restoreTestDefault() {
        properties.setEnabled(false);
    }

    @Test
    void anIdenticalSearchIsServedFromRedisUntilTheEntryIsGone() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");

        assertThat(firstPrice(search(hotel.city()))).isEqualTo(1000.0);
        Set<String> keys = cacheKeys(hotel.city());
        assertThat(keys).hasSize(1);
        Long ttlSeconds = redis.getExpire(keys.iterator().next());
        assertThat(ttlSeconds).as("TTL in seconds (30 plus or minus 20%)").isBetween(1L, 36L);

        // Change the price directly in PostgreSQL: a second identical search must still return the cached 1000.
        jdbc.update("UPDATE hotel_min_price SET price = price + 500 WHERE hotel_id = ?", hotel.hotelId());
        assertThat(firstPrice(search(hotel.city()))).as("served from Redis").isEqualTo(1000.0);

        // Once the entry is gone the next search reads PostgreSQL again.
        redis.delete(keys);
        assertThat(firstPrice(search(hotel.city()))).as("served from PostgreSQL").isEqualTo(1500.0);
    }

    @Test
    void differentSearchesDoNotShareCacheEntries() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");

        search(hotel.city());
        get("/hotels/search?city=" + hotel.city() + "&startDate=" + checkIn + "&endDate=" + checkIn.plusDays(1)
                + "&roomsCount=1", null);

        assertThat(cacheKeys(hotel.city())).as("one key per distinct search").hasSize(2);
    }

    @Test
    void aStaleSearchResultNeverCausesAnOverbooking() {
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");
        search(hotel.city()); // fills the cache: the single room is listed as available

        ResponseEntity<JsonNode> first = initBooking(newGuestToken(), hotel, checkIn, checkOut, 1);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // The cached search still lists the hotel (that is the accepted, bounded staleness)...
        assertThat(data(search(hotel.city())).path("content")).as("stale search result").hasSize(1);
        // ...but booking does not use the cache: it locks the inventory in PostgreSQL, so the last room is gone.
        ResponseEntity<JsonNode> second = initBooking(newGuestToken(), hotel, checkIn, checkOut, 1);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void nothingIsCachedWhenTheCacheIsDisabled() {
        properties.setEnabled(false);
        TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");

        assertThat(firstPrice(search(hotel.city()))).isEqualTo(1000.0);
        assertThat(cacheKeys(hotel.city())).isEmpty();

        jdbc.update("UPDATE hotel_min_price SET price = price + 500 WHERE hotel_id = ?", hotel.hotelId());
        assertThat(firstPrice(search(hotel.city()))).as("change visible immediately").isEqualTo(1500.0);
    }

    private ResponseEntity<JsonNode> search(String city) {
        ResponseEntity<JsonNode> response = get("/hotels/search?city=" + city + "&startDate=" + checkIn
                + "&endDate=" + checkOut + "&roomsCount=1", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private static double firstPrice(ResponseEntity<JsonNode> search) {
        return data(search).path("content").get(0).path("price").asDouble();
    }

    private Set<String> cacheKeys(String city) {
        // The cache key holds the URL-encoded city (place labels contain spaces and commas).
        Set<String> keys = redis.keys(SearchCache.KEY_PREFIX
                + java.net.URLEncoder.encode(city, java.nio.charset.StandardCharsets.UTF_8) + ":*");
        return keys == null ? Set.of() : keys;
    }
}
