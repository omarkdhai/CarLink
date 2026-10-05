package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Control for {@link SwaggerDisabledTest}: with springdoc at its defaults the
 * API description is served to an unauthenticated caller.
 *
 * <p>If this ever starts returning 404, then the disabled-case test is passing
 * for an unrelated reason (springdoc off the classpath, a broken
 * auto-configuration) and proves nothing.
 */
class SwaggerEnabledTest extends AbstractIntegrationTest {

    @Test
    void apiDocsIsServedToAnonymousCallersByDefault() throws Exception {
        int status = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getStatus();
        assertThat(status)
                .as("if this is 404, SwaggerDisabledTest needs a new control")
                .isEqualTo(200);
    }
}