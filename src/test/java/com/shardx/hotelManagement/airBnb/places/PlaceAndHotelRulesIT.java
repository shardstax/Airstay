package com.shardx.hotelManagement.airBnb.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Hotels are placed in real cities chosen from the place provider, and managers pick amenities and room types from fixed
 * lists. The provider is a stub here (no Internet); everything else is the real application over HTTP.
 */
class PlaceAndHotelRulesIT extends IntegrationTestBase {

    // Each test gets places of its own: the application (and its place cache) is shared by every test in this class.
    private final long puneId = newPlaceId();
    private final long goaId = newPlaceId();
    private final PlaceDto pune = new PlaceDto(puneId, "Pune", "Maharashtra", "India", "IN", 18.51957, 73.85535,
            "Pune, Maharashtra, India");
    private final PlaceDto goa = new PlaceDto(goaId, "Panaji", "Goa", "India", "IN", 15.4909, 73.8278,
            "Panaji, Goa, India");

    @MockitoBean
    private PlaceProvider provider;

    private String manager;

    @BeforeEach
    void setUp() {
        reset(provider);
        when(provider.findById(anyLong())).thenReturn(Optional.empty());
        manager = newManagerToken();
    }

    private ResponseEntity<JsonNode> put(String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private Map<String, Object> hotelBody(long placeId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Lakeview Inn");
        body.put("placeId", placeId);
        return body;
    }

    private String messageOf(ResponseEntity<JsonNode> response) {
        return response.getBody().path("error").path("message").asText()
                + response.getBody().path("error").path("subErrors").toString();
    }

    // ---- the public place search -------------------------------------------------------------------------

    @Test
    void anyoneCanSearchPlacesWithoutSigningIn() {
        when(provider.search("pun", 8)).thenReturn(List.of(pune));

        ResponseEntity<JsonNode> response = get("/places?query=pun", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode first = data(response).get(0);
        assertThat(first.path("id").asLong()).isEqualTo(puneId);
        assertThat(first.path("label").asText()).isEqualTo("Pune, Maharashtra, India");
        assertThat(first.path("countryCode").asText()).isEqualTo("IN");
    }

    @Test
    void aSearchNeedsAtLeastTwoCharacters() {
        assertThat(get("/places?query=p", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/places?query=", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/places", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aSearchIsLimitedToOneHundredCharacters() {
        assertThat(get("/places?query=" + "a".repeat(101), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anOutageOfThePlaceProviderIsA503WithAFriendlyMessage() {
        when(provider.search(anyString(), anyInt())).thenThrow(new PlaceLookupException(
                "Place search is temporarily unavailable", new RuntimeException("boom")));

        ResponseEntity<JsonNode> response = get("/places?query=pune", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(messageOf(response)).contains("temporarily unavailable").doesNotContain("boom");
    }

    @Test
    void theCatalogIsPublicAndListsTheChoices() {
        ResponseEntity<JsonNode> response = get("/catalog", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=3600");
        assertThat(data(response).path("roomTypes")).extracting(JsonNode::asText).contains("Standard", "Deluxe", "Suite");
        assertThat(data(response).path("hotelAmenities")).extracting(JsonNode::asText).contains("WiFi", "Pool");
        assertThat(data(response).path("roomAmenities")).extracting(JsonNode::asText).contains("Balcony");
    }

    // ---- creating and editing a hotel ----------------------------------------------------------------------

    @Test
    void aHotelTakesItsCityFromTheRealPlaceNotFromWhatTheClientSends() {
        when(provider.findById(puneId)).thenReturn(Optional.of(pune));
        Map<String, Object> body = hotelBody(puneId);
        body.put("city", "Atlantis"); // ignored: the city is read-only

        ResponseEntity<JsonNode> response = post("/admin/hotels", manager, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(response).path("city").asText()).isEqualTo("Pune, Maharashtra, India");
        assertThat(data(response).path("placeId").asLong()).isEqualTo(puneId);
        assertThat(data(response).path("contactInfo").path("location").asText()).isEqualTo("18.51957,73.85535");
    }

    @Test
    void aPlaceThatDoesNotExistIsRefused() {
        ResponseEntity<JsonNode> response = post("/admin/hotels", manager, hotelBody(42L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(messageOf(response)).contains("Unknown place");
    }

    @Test
    void aHotelWithoutAPlaceIsRefused() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Lakeview Inn");
        body.put("city", "Pune"); // free text is no longer a way in

        ResponseEntity<JsonNode> response = post("/admin/hotels", manager, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(messageOf(response)).contains("placeId");
    }

    @Test
    void anOutageWhileSavingAHotelIsA503AndNothingIsSaved() {
        when(provider.findById(anyLong())).thenThrow(new PlaceLookupException("Place lookup is temporarily unavailable",
                new RuntimeException()));

        ResponseEntity<JsonNode> response = post("/admin/hotels", manager, hotelBody(puneId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(data(get("/admin/hotels", manager))).isEmpty();
    }

    @Test
    void movingAHotelToAnotherPlaceMovesItsSearchableCityToo() {
        when(provider.findById(puneId)).thenReturn(Optional.of(pune));
        when(provider.findById(goaId)).thenReturn(Optional.of(goa));
        long hotelId = data(post("/admin/hotels", manager, hotelBody(puneId))).path("id").asLong();
        post("/admin/hotels/" + hotelId + "/rooms", manager,
                Map.of("type", "Deluxe", "basePrice", "1000.00", "totalCount", 2, "capacity", 2));
        assertThat(patch("/admin/hotels/" + hotelId + "/activate", manager).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT DISTINCT city FROM inventory WHERE hotel_id = ?", String.class, hotelId))
                .isEqualTo("Pune, Maharashtra, India");

        ResponseEntity<JsonNode> moved = put("/admin/hotels/" + hotelId, manager, hotelBody(goaId));

        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(moved).path("city").asText()).isEqualTo("Panaji, Goa, India");
        assertThat(jdbc.queryForObject("SELECT DISTINCT city FROM inventory WHERE hotel_id = ?", String.class, hotelId))
                .isEqualTo("Panaji, Goa, India");
        assertThat(jdbc.queryForObject("SELECT place_id FROM hotel WHERE id = ?", Long.class, hotelId)).isEqualTo(goaId);
    }

    @Test
    void anAmenityOutsideTheListIsRefused() {
        when(provider.findById(puneId)).thenReturn(Optional.of(pune));
        Map<String, Object> body = hotelBody(puneId);
        body.put("amenities", List.of("WiFi", "Helipad"));

        ResponseEntity<JsonNode> response = post("/admin/hotels", manager, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(messageOf(response)).contains("amenities");
    }

    @Test
    void aBadPhoneNumberEmailAndPhotoAreEachRefused() {
        when(provider.findById(puneId)).thenReturn(Optional.of(pune));
        Map<String, Object> body = hotelBody(puneId);
        body.put("contactInfo", Map.of("phoneNumber", "call me", "email", "not-an-email"));
        body.put("photos", List.of("javascript:alert(1)"));

        ResponseEntity<JsonNode> response = post("/admin/hotels", manager, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(messageOf(response)).contains("phoneNumber", "email", "photos");
    }

    @Test
    void aValidHotelWithEverythingFilledInIsAccepted() {
        when(provider.findById(puneId)).thenReturn(Optional.of(pune));
        Map<String, Object> body = hotelBody(puneId);
        body.put("amenities", List.of("WiFi", "Pool", "Spa"));
        body.put("photos", List.of("https://images.example.com/front.jpg"));
        body.put("contactInfo", Map.of("phoneNumber", "+91 98765 43210", "email", "front@lakeview.example",
                "address", "12 MG Road"));

        ResponseEntity<JsonNode> response = post("/admin/hotels", manager, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(response).path("contactInfo").path("address").asText()).isEqualTo("12 MG Road");
    }

    // ---- rooms ---------------------------------------------------------------------------------------------

    private long newHotel() {
        when(provider.findById(puneId)).thenReturn(Optional.of(pune));
        return data(post("/admin/hotels", manager, hotelBody(puneId))).path("id").asLong();
    }

    @Test
    void aRoomTypeOutsideTheListIsRefused() {
        long hotelId = newHotel();

        ResponseEntity<JsonNode> response = post("/admin/hotels/" + hotelId + "/rooms", manager,
                Map.of("type", "Penthouse", "basePrice", "1000.00", "totalCount", 2, "capacity", 2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(messageOf(response)).contains("type");
    }

    @Test
    void aRoomAmenityOutsideItsListIsRefused() {
        long hotelId = newHotel();

        ResponseEntity<JsonNode> response = post("/admin/hotels/" + hotelId + "/rooms", manager,
                Map.of("type", "Suite", "basePrice", "1000.00", "totalCount", 2, "capacity", 2, "amenities", List.of("Pool")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aValidRoomIsAccepted() {
        long hotelId = newHotel();

        ResponseEntity<JsonNode> response = post("/admin/hotels/" + hotelId + "/rooms", manager,
                Map.of("type", "Suite", "basePrice", "4500.00", "totalCount", 3, "capacity", 4,
                        "amenities", List.of("Balcony", "Mini bar")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(response).path("type").asText()).isEqualTo("Suite");
    }
}
