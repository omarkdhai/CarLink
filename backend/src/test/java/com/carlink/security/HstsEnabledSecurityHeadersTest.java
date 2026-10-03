package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * HSTS with the opt-in enabled, as the prod profile sets it.
 *
 * <p>Lives in its own file because it needs a different property value, and
 * therefore a separate application context, from {@link SecurityHeadersTest}.
 */
@TestPropertySource(properties = "carlink.security.hsts-enabled=true")
class HstsEnabledSecurityHeadersTest extends AbstractIntegrationTest {

    private static final RequestPostProcessor HTTPS = request -> {
        request.setSecure(true);
        return request;
    };

    @Test
    void sendsHstsWithSubdomainCommitment() throws Exception {
        var response = mockMvc.perform(get("/actuator/health").with(HTTPS))
                .andReturn().getResponse();
        assertThat(response.getHeader("Strict-Transport-Security"))
                .isNotNull()
                .contains("max-age=31536000")
                .contains("includeSubDomains");
    }

    @Test
    void withholdsHstsOnPlainHttpEvenWhenEnabled() throws Exception {
        // Browsers ignore HSTS over plain HTTP anyway; Spring withholds the
        // header too. Asserted so the behaviour is pinned rather than assumed.
        var response = mockMvc.perform(get("/actuator/health")).andReturn().getResponse();
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }

    @Test
    void stillSendsNoSniff() throws Exception {
        var response = mockMvc.perform(get("/actuator/health")).andReturn().getResponse();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}