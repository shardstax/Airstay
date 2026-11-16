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
}
