package com.shardx.hotelManagement.airBnb.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL, one Redis and one Kafka container for the whole test run. They are beans of the (cached) test
 * context, so every integration test class shares them; Flyway migrates PostgreSQL exactly like production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    }

    /**
     * Same broker image as docker-compose.yml.
     *
     * <p>Testcontainers' KafkaContainer sets the listeners to 0.0.0.0, and apache/kafka 3.9 then refuses to start
     * ("advertised.listeners cannot use the nonroutable meta-address 0.0.0.0") even though the advertised addresses it
     * adds are correct. Listeners with an empty host mean the same thing (all interfaces) and are accepted.
     */
    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.0"))
                .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094");
    }
}
