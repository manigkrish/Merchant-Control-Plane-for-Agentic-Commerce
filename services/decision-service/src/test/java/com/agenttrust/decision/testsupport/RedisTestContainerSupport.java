package com.agenttrust.decision.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public abstract class RedisTestContainerSupport {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.4-alpine");
    private static final int REDIS_PORT = 6379;

    private static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(REDIS_PORT)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(30));

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerRedisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisTestContainerSupport::redisHostForClient);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));

        // Keep tests snappy and reduce shutdown noise.
        registry.add("spring.data.redis.timeout", () -> "2s");
        registry.add("spring.data.redis.lettuce.shutdown-timeout", () -> "0ms");
    }

    private static String redisHostForClient() {
        // In WSL/Docker Desktop, "localhost" can sometimes cause unresolved/IPv6 quirks in clients.
        String host = REDIS.getHost();
        if (host == null || host.isBlank() || "localhost".equalsIgnoreCase(host)) {
            return "127.0.0.1";
        }
        return host;
    }
}
