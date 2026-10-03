package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Response-header regression tests, default profile.
 *
 * <p>The header block in {@code SecurityConfig} called
 * {@code contentTypeOptions(...disable())} and {@code cacheControl(...disable())}
 * directly beneath comments reading "prevent MIME sniffing" and "disable cache
 * for sensitive endpoints". Spring Security treats an explicit
 * {@code disable()} as "remove this writer from the chain" and does not fall back
 * to its default, so {@code nosniff} was never sent and {@code Cache-Control}
 * was absent entirely — the opposite of both comments. A comment is not a
 * control, hence these assertions.
 */
class SecurityHeadersTest extends AbstractIntegrationTest {

    private static final String HEALTH = "/actuator/health";

    /**
     * Spring's HstsHeaderWriter only emits over a secure request. MockMvc
     * defaults to plain HTTP, so every HSTS assertion has to ask for TLS
     * explicitly or it passes for the wrong reason.
     */
    private static final RequestPostProcessor HTTPS = request -> {
        request.setSecure(true);
        return request;
    };

    @Test
    void sendsNoSniff() throws Exception {
        var response = mockMvc.perform(get(HEALTH)).andReturn().getResponse();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void deniesFraming() throws Exception {
        var response = mockMvc.perform(get(HEALTH)).andReturn().getResponse();
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void sendsContentSecurityPolicy() throws Exception {
        var response = mockMvc.perform(get(HEALTH)).andReturn().getResponse();
        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'self'");
    }

    @Test
    void sendsReferrerPolicy() throws Exception {
        var response = mockMvc.perform(get(HEALTH)).andReturn().getResponse();
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void suppressesCaching() throws Exception {
        var response = mockMvc.perform(get(HEALTH)).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void omitsHstsUnlessExplicitlyEnabled() throws Exception {
        // Asserted over a *secure* request, so this would still fail if the
        // feature were on. Off by default: sending it over plain HTTP is
        // ignored by browsers, and includeSubDomains cannot be withdrawn once
        // a browser has cached it.
        var response = mockMvc.perform(get(HEALTH).with(HTTPS)).andReturn().getResponse();
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }

    @Test
    void headersSurviveOnUnauthenticatedRoutesToo() throws Exception {
        // The QR page is the most exposed surface in the app.
        mockMvc.perform(get("/c/nonexistent-token"))
                .andExpect(status().isNotFound());
        var response = mockMvc.perform(get("/c/nonexistent-token")).andReturn().getResponse();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}