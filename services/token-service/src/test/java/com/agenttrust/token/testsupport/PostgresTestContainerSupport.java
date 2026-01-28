package com.agenttrust.token.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Postgres Testcontainers support for token-service integration tests.
 *
 * Starts a single Postgres container for the test JVM and wires Spring datasource properties.
 */
public abstract class PostgresTestContainerSupport {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:15.2-alpine");

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("agenttrust")
            .withUsername("agenttrust")
            .withPassword("agenttrust");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Make Flyway explicit for tests.
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
