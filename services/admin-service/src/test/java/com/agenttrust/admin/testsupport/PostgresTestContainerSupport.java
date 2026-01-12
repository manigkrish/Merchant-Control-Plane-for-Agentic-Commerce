package com.agenttrust.admin.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Shared Postgres Testcontainers support for admin-service integration tests.
 *
 * Tests that extend this class will run against a real ephemeral Postgres container
 * (no dependency on your local docker-compose DB).
 */
public abstract class PostgresTestContainerSupport {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("agenttrust_test")
                    .withUsername("agenttrust")
                    .withPassword("agenttrust");

    static {
        // Start once for the JVM test run to keep tests fast.
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Flyway migrations are the schema source-of-truth.
        registry.add("spring.flyway.enabled", () -> "true");

        // JPA must validate schema against migrations.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
    }
}
