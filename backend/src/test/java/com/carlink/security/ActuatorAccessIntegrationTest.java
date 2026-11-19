package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import com.carlink.auth.service.JwtService;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security plan S-05: operational endpoints are for administrators only.
 * Health stays public (load balancers / compose healthcheck); metrics and the
 * Prometheus scrape used to answer 200 to any signed-in owner.
 */
class ActuatorAccessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void metricsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void ownersCannotReadMetrics() throws Exception {
        String owner = bearer(Role.USER);
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", owner))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics").header("Authorization", owner))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminsCanReadMetrics() throws Exception {
        String admin = bearer(Role.ADMIN);
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics").header("Authorization", admin))
                .andExpect(status().isOk());
    }

    private String bearer(Role role) {
        // Persisted: since S-10 a token is only honoured for a live, active account.
        UUID id = UUID.randomUUID();
        User user = userRepository.save(User.builder()
                .id(id)
                .email(role.name().toLowerCase() + "-" + id + "@actuator.test")
                .passwordHash("x")
                .role(role)
                .active(true)
                .build());
        return "Bearer " + jwtService.issueAccessToken(user);
    }
}
