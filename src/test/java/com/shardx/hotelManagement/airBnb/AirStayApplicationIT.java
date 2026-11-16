package com.shardx.hotelManagement.airBnb;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
                case "roomsCount", "page", "size" -> assertThat(required).as("%s is optional", name).isFalse();
                case "city", "startDate", "endDate" -> assertThat(required).as("%s is required", name).isTrue();
                default -> throw new AssertionError("Unexpected search parameter: " + name);
            }
        }
    }
}
