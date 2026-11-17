package com.shardx.hotelManagement.airBnb.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helpers for the inventory and pricing tests: a live hotel with two room types (10 rooms each) whose base prices are 2000
 * and 2500. The test profile has no holidays, so prices are deterministic.
 */
abstract class PricingTestBase extends IntegrationTestBase {

    protected record Hotel(long id, String city, long standardRoomId, long suiteRoomId, String managerToken) {
    }

    protected Hotel activeHotelWithTwoRoomTypes() {
        String manager = newManagerToken();
        JsonNode created = data(post("/admin/hotels", manager, Map.of("name", "Pricing Hotel", "placeId", newPlaceId())));
        long hotelId = created.path("id").asLong();
        String city = created.path("city").asText();
        long standard = data(post("/admin/hotels/" + hotelId + "/rooms", manager, Map.of(
                "type", "Standard", "basePrice", "2000.00", "totalCount", 10, "capacity", 2))).path("id").asLong();
        long suite = data(post("/admin/hotels/" + hotelId + "/rooms", manager, Map.of(
                "type", "Suite", "basePrice", "2500.00", "totalCount", 10, "capacity", 4))).path("id").asLong();
        assertThat(patch("/admin/hotels/" + hotelId + "/activate", manager).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        return new Hotel(hotelId, city, standard, suite, manager);
    }

    /** PUT, PATCH or DELETE with a JSON body (the base class only has the bodiless variants). */
    protected ResponseEntity<JsonNode> send(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    protected BigDecimal nightPrice(long roomId, LocalDate date) {
        return jdbc.queryForObject("SELECT price FROM inventory WHERE room_id = ? AND date = ?",
                BigDecimal.class, roomId, date);
    }

    protected BigDecimal fromPrice(long hotelId, LocalDate date) {
        return jdbc.queryForObject("SELECT price FROM hotel_min_price WHERE hotel_id = ? AND date = ?",
                BigDecimal.class, hotelId, date);
    }
}
