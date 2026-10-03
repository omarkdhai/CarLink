package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * {@code management.endpoints.web.exposure.include} lists {@code prometheus},
 * but the actuator starter ships no Prometheus registry. The name resolved to a
 * path that did not exist, so this asserts the registry is actually present.
 */
class PrometheusMetricsTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void registersPrometheusMeterRegistry() {
        assertThat(context.getBeanNamesForType(PrometheusMeterRegistry.class))
                .as("actuator cannot serve /actuator/prometheus without this registry")
                .isNotEmpty();
    }

    @Test
    void exposesPrometheusPath() throws Exception {
        // Not 404: a public endpoint would be a leak, an authenticated one a 401.
        // Either way it proves the path is routed rather than unknown.
        int status = mockMvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getStatus();
        assertThat(status)
                .as("/actuator/prometheus must exist but must not be public")
                .isNotEqualTo(404)
                .isEqualTo(401);
    }

    @Test
    void keepsHealthPublic() throws Exception {
        // The compose healthcheck depends on this staying unauthenticated.
        int status = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(200);
    }
}