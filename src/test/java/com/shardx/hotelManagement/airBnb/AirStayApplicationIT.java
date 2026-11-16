package com.shardx.hotelManagement.airBnb;

import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The application starts against a real PostgreSQL, Flyway migrates it and Hibernate validates the schema. */
class AirStayApplicationIT extends IntegrationTestBase {

    @Test
    void startsWithTheMigratedSchema() {
        Integer appliedMigrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class);

        assertThat(appliedMigrations).isGreaterThanOrEqualTo(1);
    }
}
