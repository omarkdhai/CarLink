package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import com.carlink.auth.service.JwtService;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security plan S-10: an access token stops working as soon as its account is
 * deactivated or its role changes, not when the JWT expires; admin access
 * tokens are short-lived.
 */
class SessionRevocationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void deactivatedOwnerLosesAccessImmediately() throws Exception {
        User owner = save(Role.USER);
        String ownerToken = bearer(owner);
        String adminToken = bearer(save(Role.ADMIN));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", owner.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().is2xxSuccessful());

        // Same, still unexpired JWT: refused at once (cached state was evicted).
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", ownerToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void demotedAdminLosesAdminRoutesImmediately() throws Exception {
        User demoted = save(Role.ADMIN);
        String demotedToken = bearer(demoted);
        String otherAdmin = bearer(save(Role.ADMIN));

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", demotedToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/{id}/role", demoted.getId())
                        .header("Authorization", otherAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "USER"))))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", demotedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfAnAccountThatNoLongerExistsIsRefused() throws Exception {
        User ghost = User.builder().id(UUID.randomUUID()).email("ghost@example.com")
                .role(Role.ADMIN).active(true).build();
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(ghost)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminAccessTokensAreShortLived() throws Exception {
        User admin = save(Role.ADMIN);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", admin.getEmail(), "password", "Secret1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(5 * 60));

        User owner = save(Role.USER);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", owner.getEmail(), "password", "Secret1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(15 * 60));
    }

    private User save(Role role) {
        UUID id = UUID.randomUUID();
        return userRepository.save(User.builder()
                .id(id).email("s10-" + id + "@example.com")
                .passwordHash(passwordEncoder.encode("Secret1234"))
                .firstName("S").lastName("Ten").role(role).active(true).emailVerified(true)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issueAccessToken(user);
    }
}
