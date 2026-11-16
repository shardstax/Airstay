package com.shardx.hotelManagement.airBnb.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.service.CheckoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base for integration tests: the real application on a random port, a real PostgreSQL
 * (Testcontainers) and real HTTP calls. Only the Stripe boundary (CheckoutService) is mocked;
 * webhooks are sent signed, exactly as Stripe sends them.
 *
 * Every test creates its own users and hotel in a unique city, so tests never see each other's data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Password123!";

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected CheckoutService checkoutService;

    @Value("${stripe.webhook.secret}")
    protected String webhookSecret;

    protected record TestHotel(long hotelId, long roomId, String city) {
    }

    // ---- users ------------------------------------------------------------------------------

    protected String newGuestToken() {
        return signUpAndLogin("GUEST");
    }

    protected String newManagerToken() {
        return signUpAndLogin("HOTEL_MANAGER");
    }

    private String signUpAndLogin(String accountType) {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<JsonNode> signup = post("/auth/signup", null,
                Map.of("email", email, "password", PASSWORD, "name", "Test User", "accountType", accountType));
        assertThat(signup.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> login = post("/auth/login", null, Map.of("email", email, "password", PASSWORD));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return data(login).path("accessToken").asText();
    }

    protected long addGuest(String token) {
        ResponseEntity<JsonNode> response = post("/users/guests", token, Map.of("name", "Guest " + UUID.randomUUID()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return data(response).path("id").asLong();
    }

    // ---- hotels -----------------------------------------------------------------------------

    /** Creates a hotel with one room type, activates it (one year of inventory + prices) and returns ids. */
    protected TestHotel createActiveHotel(String managerToken, int roomCount, String basePrice) {
        String city = "City-" + UUID.randomUUID();

        ResponseEntity<JsonNode> hotel = post("/admin/hotels", managerToken, Map.of("name", "Test Hotel", "city", city));
        assertThat(hotel.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long hotelId = data(hotel).path("id").asLong();

        ResponseEntity<JsonNode> room = post("/admin/hotels/" + hotelId + "/rooms", managerToken, Map.of(
                "type", "Deluxe", "basePrice", basePrice, "totalCount", roomCount, "capacity", 2));
        assertThat(room.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long roomId = data(room).path("id").asLong();

        ResponseEntity<JsonNode> activation = patch("/admin/hotels/" + hotelId + "/activate", managerToken);
        assertThat(activation.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        return new TestHotel(hotelId, roomId, city);
    }

    // ---- bookings ---------------------------------------------------------------------------

    protected ResponseEntity<JsonNode> initBooking(String token, TestHotel hotel, LocalDate checkIn,
                                                   LocalDate checkOut, int roomsCount) {
        return post("/bookings/init", token, Map.of(
                "hotelId", hotel.hotelId(),
                "roomId", hotel.roomId(),
                "checkInDate", checkIn.toString(),
                "checkOutDate", checkOut.toString(),
                "roomsCount", roomsCount));
    }

    protected String bookingStatus(String token, long bookingId) {
        return data(get("/bookings/" + bookingId + "/status", token)).path("bookingStatus").asText();
    }

    protected ResponseEntity<JsonNode> sendStripeEvent(String type, String sessionId, String paymentStatus,
                                                       String sessionStatus) {
        String payload = StripeTestEvents.checkoutSessionEvent(type, sessionId, paymentStatus, sessionStatus);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Stripe-Signature", StripeTestEvents.signatureHeader(payload, webhookSecret));
        return rest.exchange("/webhook/payment", HttpMethod.POST, new HttpEntity<>(payload, headers), JsonNode.class);
    }

    // ---- inventory (read straight from the database) ----------------------------------------

    protected int reservedCount(long roomId, LocalDate night) {
        return jdbc.queryForObject("SELECT reserved_count FROM inventory WHERE room_id = ? AND date = ?",
                Integer.class, roomId, night);
    }

    protected int bookedCount(long roomId, LocalDate night) {
        return jdbc.queryForObject("SELECT booked_count FROM inventory WHERE room_id = ? AND date = ?",
                Integer.class, roomId, night);
    }

    // ---- HTTP helpers -----------------------------------------------------------------------

    protected ResponseEntity<JsonNode> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> post(String path, String token, Object body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> patch(String path, String token) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(headers(token)), JsonNode.class);
    }

    protected static JsonNode data(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).as("response body").isNotNull();
        return response.getBody().path("data");
    }

    protected static JsonNode error(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).as("response body").isNotNull();
        return response.getBody().path("error");
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    protected static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
