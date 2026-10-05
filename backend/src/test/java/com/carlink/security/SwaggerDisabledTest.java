package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The prod profile sets {@code springdoc.api-docs.enabled=false} and
 * {@code springdoc.swagger-ui.enabled=false}. This proves that lever works:
 * with it applied, the documented API surface stops existing rather than
 * merely becoming unreachable.
 *
 * <p>Worth testing because both paths are {@code permitAll} in
 * {@code SecurityConfig}. If springdoc ever stopped honouring the property,
 * anonymous visitors would be handed every endpoint, DTO and validation
 * constraint, and nothing else in the suite would notice.
 *
 * <p>{@link SwaggerEnabledTest} is the control: with the default settings the
 * same request returns 200, so a passing assertion here cannot be vacuous.
 */
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class SwaggerDisabledTest extends AbstractIntegrationTest {

    @Test
    void apiDocsIsGone() throws Exception {
        assertThat(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/v3/api-docs"))
                .andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void swaggerUiIsGone() throws Exception {
        assertThat(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/swagger-ui/index.html"))
                .andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void theRestOfTheApiStillWorks() throws Exception {
        // Guards against "disabling springdoc" accidentally taking the app with
        // it: a public health probe must still answer.
        assertThat(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/actuator/health"))
                .andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }
}