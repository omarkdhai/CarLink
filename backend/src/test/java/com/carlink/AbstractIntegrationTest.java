package com.carlink;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that need a real PostgreSQL and Redis.
 *
 * <p>Containers are started once in a static holder and shared by every
 * integration test class. Sharing a single long-lived set is essential:
 * Spring's TestContext framework caches one {@code ApplicationContext} for all
 * classes with identical configuration, so each class must point at the same,
 * still-running datasource/Redis — otherwise the second class reuses a cached
 * context whose {@code @Container} database was already stopped.</p>
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    /**
     * Container holder. {@code static {} } starts both containers exactly once
     * when the first integration test loads, and they stay up for the whole
     * JVM so every cached application context can reach them.
     */
    static final class Containers {
        static final PostgreSQLContainer<?> POSTGRES =
                new PostgreSQLContainer<>("postgres:15-alpine")
                        .withDatabaseName("carlink_test")
                        .withUsername("test")
                        .withPassword("test");
        @SuppressWarnings("rawtypes")
        static final GenericContainer REDIS =
                new GenericContainer<>("redis:7-alpine")
                        .withExposedPorts(6379);

        static {
            POSTGRES.start();
            REDIS.start();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Containers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", Containers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", Containers.POSTGRES::getPassword);
        registry.add("spring.data.redis.host", () -> Containers.REDIS.getHost());
        registry.add("spring.data.redis.port", () -> Containers.REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }
}
