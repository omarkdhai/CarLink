package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import com.carlink.auth.service.JwtService;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security plan S-25: security events reach Prometheus, and the static scrape
 * token opens /actuator/prometheus and nothing else.
 */
class SecurityMonitoringIntegrationTest extends AbstractIntegrationTest {

    private static final String SCRAPE = "Bearer integration-test-scrape-token-0123456789abcdef";

    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;

    @Test
    void failedLoginsShowUpAsSecurityEventsWithoutPersonalData() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-s25@example.com\",\"password\":\"Wrong-pass-123\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus").header("Authorization", SCRAPE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "carlink_security_events_total{detail=\"none\",type=\"login_failure\"")))
                .andExpect(content().string(not(containsString("nobody-s25"))));
    }

    @Test
    void scrapeTokenOpensPrometheusOnly() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header("Authorization", SCRAPE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", SCRAPE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer wrong-token-0123456789abcdefghijk"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownersStillCannotReadMetrics() throws Exception {
        UUID id = UUID.randomUUID();
        User owner = userRepository.save(User.builder().id(id).email("s25-" + id + "@example.com")
                .passwordHash("x").role(Role.USER).active(true).build());
        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner)))
                .andExpect(status().isForbidden());
    }
}
