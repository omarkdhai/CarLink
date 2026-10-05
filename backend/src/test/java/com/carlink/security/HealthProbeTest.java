package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Probe wiring for orchestrators and load balancers.
 *
 * <p>Spring's readiness group contains only {@code readinessState} by default,
 * so it reports UP even when Postgres or Redis is unreachable -- at which point
 * the app cannot resolve a scan or rate-limit anything. Naming {@code db} and
 * {@code redis} makes readiness mean "can actually serve traffic".
 */
class HealthProbeTest extends AbstractIntegrationTest {

    @Autowired
    private Environment env;

    @Test
    void readinessCoversDatabaseAndCache() {
        assertThat(env.getProperty("management.endpoint.health.group.readiness.include"))
                .contains("readinessState")
                .contains("db")
                .contains("redis");
    }

    @Test
    void livenessExcludesDependencies() {
        // Deliberate: if liveness included db/redis, a database blip would make
        // the orchestrator restart an otherwise healthy app, turning a
        // recoverable dependency failure into a restart loop.
        String include = env.getProperty("management.endpoint.health.group.liveness.include");
        assertThat(include).isNotNull().doesNotContain("db").doesNotContain("redis");
    }

    @Test
    void readinessReportsUpWhenDependenciesAreUp() throws Exception {
        // Testcontainers provides a live Postgres and Redis here, so a correct
        // readiness group must actually reach UP.
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void livenessReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void probesStayUnauthenticatedForOrchestrators() throws Exception {
        // A probe that needs a token cannot be used by Docker/Kubernetes.
        assertThat(mockMvc.perform(get("/actuator/health/readiness"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(get("/actuator/health/liveness"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}