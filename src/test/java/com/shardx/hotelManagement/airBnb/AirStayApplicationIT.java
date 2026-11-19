package com.shardx.hotelManagement.airBnb;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The application starts against a real PostgreSQL, Flyway migrates it and Hibernate validates the schema. */
class AirStayApplicationIT extends IntegrationTestBase {

    @Test
    void startsWithTheMigratedSchema() {
        Integer appliedMigrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class);

        assertThat(appliedMigrations).isGreaterThanOrEqualTo(1);
    }

    /** Docker and load balancers call these without a token; the plain health check includes the database. */
    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void healthEndpointsArePublicAndUp(String path) {
        ResponseEntity<JsonNode> response = get(path, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
    }

    /**
     * Swagger UI will not send a request while a parameter marked required is empty, so the docs must not mark
     * parameters that have defaults (roomsCount, page, size) as required. Found by driving Swagger UI in a browser.
     */
    @Test
    void searchParametersWithDefaultsAreOptionalInTheApiDocs() {
        JsonNode docs = get("/v3/api-docs", null).getBody();
        assertThat(docs).isNotNull();

        JsonNode parameters = docs.path("paths").path("/hotels/search").path("get").path("parameters");
        assertThat(parameters).isNotEmpty();
        for (JsonNode parameter : parameters) {
            String name = parameter.path("name").asText();
            boolean required = parameter.path("required").asBoolean();
            switch (name) {
                case "roomsCount", "page", "size", "amenities" -> assertThat(required).as("%s is optional", name).isFalse();
                case "city", "startDate", "endDate" -> assertThat(required).as("%s is required", name).isTrue();
                default -> throw new AssertionError("Unexpected search parameter: " + name);
            }
        }
    }

    /** Swagger UI labels a status code "Undocumented" unless the API docs list it, so docs must match behaviour. */
    @Test
    void successStatusCodesAreDocumentedAsTheyAreReturned() {
        JsonNode docs = get("/v3/api-docs", null).getBody();
        assertThat(docs).isNotNull();
        JsonNode paths = docs.path("paths");

        assertThat(documentedStatuses(paths, "/auth/signup", "post")).containsExactly("201");
        assertThat(documentedStatuses(paths, "/bookings/init", "post")).containsExactly("201");
        assertThat(documentedStatuses(paths, "/admin/hotels", "post")).containsExactly("201");
        assertThat(documentedStatuses(paths, "/bookings/{bookingId}/cancel", "post")).containsExactly("204");
        assertThat(documentedStatuses(paths, "/admin/hotels/{hotelId}/activate", "patch")).containsExactly("204");
        assertThat(documentedStatuses(paths, "/webhook/payment", "post")).containsExactly("204");
        assertThat(documentedStatuses(paths, "/hotels/search", "get")).containsExactly("200");
    }

    /**
     * The frontend generates its TypeScript types from this spec. A field the API always returns must be marked required, or
     * the generated type makes it optional and every screen has to guard against a value that is never missing. Fields that
     * can really be absent (photos, amenities, ...) must stay optional so the type tells the truth in both directions.
     */
    @Test
    void fieldsThatAreAlwaysReturnedAreRequiredInTheApiDocs() {
        JsonNode schemas = get("/v3/api-docs", null).getBody().path("components").path("schemas");

        assertRequired(schemas, "HotelPriceResponseDto", "id", "name", "city", "price");
        assertRequired(schemas, "RoomPriceResponseDto", "id", "type", "price");
        assertRequired(schemas, "HotelInfoDto", "hotel", "rooms");
        assertRequired(schemas, "BookingDto", "id", "hotelId", "hotelName", "roomId", "roomType", "roomsCount", "checkInDate", "checkOutDate", "maxGuests",
                "bookingStatus", "amount");
        assertRequired(schemas, "UserDto", "id", "email", "name", "roles");
        assertRequired(schemas, "InventoryDto", "id", "date", "bookedCount", "reservedCount", "totalCount", "surgeFactor", "price", "closed");
        assertRequired(schemas, "HotelReportDto", "bookingCount", "totalRevenue", "avgRevenue");
        assertRequired(schemas, "LoginResponseDto", "accessToken");
        assertRequired(schemas, "BookingStatusResponseDto", "bookingStatus");
        assertRequired(schemas, "PlaceDto", "id", "name", "country", "countryCode", "latitude", "longitude", "label");
        assertRequired(schemas, "CatalogDto", "hotelAmenities", "roomAmenities", "roomTypes");
        assertRequired(schemas, "HotelDto", "name", "placeId");
        assertRequired(schemas, "BookingPaymentInitResponseDto", "sessionUrl");

        assertThat(requiredNames(schemas, "HotelPriceResponseDto")).doesNotContain("photos", "amenities", "contactInfo");
        assertThat(requiredNames(schemas, "BookingDto")).doesNotContain("expiresAt", "guests");
        assertThat(requiredNames(schemas, "PlaceDto")).doesNotContain("region");
        assertThat(requiredNames(schemas, "HotelDto")).as("the city is derived from the place").doesNotContain("city");
    }

    private static void assertRequired(JsonNode schemas, String schema, String... fields) {
        assertThat(schemas.has(schema)).as("schema %s exists", schema).isTrue();
        assertThat(requiredNames(schemas, schema)).as("required fields of %s", schema).contains(fields);
    }

    private static List<String> requiredNames(JsonNode schemas, String schema) {
        List<String> names = new ArrayList<>();
        schemas.path(schema).path("required").forEach(node -> names.add(node.asText()));
        return names;
    }

    private static List<String> documentedStatuses(JsonNode paths, String path, String method) {
        List<String> statuses = new ArrayList<>();
        paths.path(path).path(method).path("responses").fieldNames().forEachRemaining(statuses::add);
        return statuses;
    }
}
