package com.shardx.hotelManagement.airBnb.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Authentication, authorization, ownership and error responses, all through the real security filter chain. */
class SecurityAndErrorsIT extends IntegrationTestBase {

    @Nested
    class Authentication {

        @Test
        void protectedEndpointWithoutTokenIs401() {
            ResponseEntity<JsonNode> response = get("/users/profile", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(error(response).path("status").asText()).isEqualTo("UNAUTHORIZED");
        }

        @Test
        void publicSearchNeedsNoToken() {
            LocalDate checkIn = LocalDate.now().plusDays(5);
            ResponseEntity<JsonNode> response = get("/hotels/search?city=Nowhere&startDate=" + checkIn
                    + "&endDate=" + checkIn.plusDays(1), null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void wrongPasswordIs401() {
            String email = "user-" + UUID.randomUUID() + "@example.com";
            post("/auth/signup", null, Map.of("email", email, "password", PASSWORD, "name", "Test"));

            ResponseEntity<JsonNode> response = post("/auth/login", null, Map.of("email", email, "password", "wrong-password"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void refreshCookieIssuesAWorkingAccessTokenButIsRejectedAsABearerToken() {
            String email = "user-" + UUID.randomUUID() + "@example.com";
            post("/auth/signup", null, Map.of("email", email, "password", PASSWORD, "name", "Test"));
            ResponseEntity<JsonNode> login = post("/auth/login", null, Map.of("email", email, "password", PASSWORD));
            String refreshToken = refreshTokenFrom(login);

            // The refresh token must not work as an access token...
            assertThat(get("/users/profile", refreshToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // ...but the cookie gets a new access token that does.
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.COOKIE, "refreshToken=" + refreshToken);
            ResponseEntity<JsonNode> refreshed = rest.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(headers), JsonNode.class);
            assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
            String accessToken = data(refreshed).path("accessToken").asText();
            assertThat(get("/users/profile", accessToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void refreshWithoutCookieIs401() {
            assertThat(post("/auth/refresh", null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void loginCookieIsHttpOnlyAndScopedToAuthEndpoints() {
            String email = "user-" + UUID.randomUUID() + "@example.com";
            post("/auth/signup", null, Map.of("email", email, "password", PASSWORD, "name", "Test"));
            ResponseEntity<JsonNode> login = post("/auth/login", null, Map.of("email", email, "password", PASSWORD));

            String cookie = refreshCookieHeader(login);
            assertThat(cookie).contains("HttpOnly").contains("Path=/api/v1/auth").contains("SameSite=Strict");
        }
    }

    @Nested
    class Authorization {

        @Test
        void guestCannotUseManagerEndpoints() {
            ResponseEntity<JsonNode> response = get("/admin/hotels", newGuestToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void managerCannotSeeAnotherManagersHotel() {
            TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");

            ResponseEntity<JsonNode> response = get("/admin/hotels/" + hotel.hotelId(), newManagerToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void guestCannotReadAnotherGuestsBooking() {
            TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");
            LocalDate checkIn = LocalDate.now().plusDays(60);
            long bookingId = data(initBooking(newGuestToken(), hotel, checkIn, checkIn.plusDays(1), 1)).path("id").asLong();

            ResponseEntity<JsonNode> response = get("/bookings/" + bookingId + "/status", newGuestToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void guestCannotAttachSomeoneElsesSavedGuest() {
            TestHotel hotel = createActiveHotel(newManagerToken(), 1, "1000.00");
            LocalDate checkIn = LocalDate.now().plusDays(61);
            String guest = newGuestToken();
            long bookingId = data(initBooking(guest, hotel, checkIn, checkIn.plusDays(1), 1)).path("id").asLong();
            long strangersGuestId = addGuest(newGuestToken());

            ResponseEntity<JsonNode> response = post("/bookings/" + bookingId + "/addGuests", guest, List.of(strangersGuestId));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class Errors {

        @Test
        void invalidBookingRequestIs400WithFieldErrors() {
            LocalDate checkIn = LocalDate.now().plusDays(10);
            ResponseEntity<JsonNode> response = post("/bookings/init", newGuestToken(), Map.of(
                    "hotelId", 1, "roomId", 1, "roomsCount", 0,
                    "checkInDate", checkIn.toString(), "checkOutDate", checkIn.minusDays(1).toString()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error(response).path("subErrors").toString())
                    .contains("roomsCount")
                    .contains("dateRangeValid");
        }

        @Test
        void duplicateSignUpIs409() {
            String email = "user-" + UUID.randomUUID() + "@example.com";
            Map<String, Object> body = Map.of("email", email, "password", PASSWORD, "name", "Test");
            post("/auth/signup", null, body);

            assertThat(post("/auth/signup", null, body).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void activatingTwiceIs409() {
            String manager = newManagerToken();
            TestHotel hotel = createActiveHotel(manager, 1, "1000.00");

            assertThat(patch("/admin/hotels/" + hotel.hotelId() + "/activate", manager).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void unknownRouteIs404NotA500() {
            assertThat(get("/does-not-exist", newGuestToken()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void webhookWithABadSignatureIs400() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Stripe-Signature", "t=1,v1=forged");

            ResponseEntity<JsonNode> response = rest.exchange("/webhook/payment", HttpMethod.POST,
                    new HttpEntity<>("{\"id\":\"evt_forged\"}", headers), JsonNode.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private static String refreshCookieHeader(ResponseEntity<JsonNode> login) {
        List<String> cookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).as("Set-Cookie headers").isNotNull();
        return cookies.stream()
                .filter(cookie -> cookie.startsWith("refreshToken="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No refreshToken cookie in " + cookies));
    }

    private static String refreshTokenFrom(ResponseEntity<JsonNode> login) {
        String cookie = refreshCookieHeader(login);
        return cookie.substring("refreshToken=".length(), cookie.indexOf(';'));
    }
}
