package com.carlink;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 smoke test — verifies the Spring context boots with
 * PostgreSQL + Redis (Testcontainers) and Flyway migrations applied.
 */
class ContextSmokeTest extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        assertThat(true).isTrue();
    }
}