package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security plan S-04: lockouts cannot be aimed at someone else; stuffing is capped per IP. */
@TestPropertySource(properties = {
        "carlink.throttle.login-ip.max=8",
        "carlink.ratelimit.max-login-failures=3",
})
class LoginHardeningIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void attackerLocksOnlyItselfOutNotTheOwner() throws Exception {
        String email = newUser();
        String attacker = "10.4.0.1";
        for (int i = 0; i < 3; i++) {
            login(attacker, email, "WrongPass1").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        // The attacker's (IP, e-mail) pair is now locked, even with the right password...
        login(attacker, email, "Secret123").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
        // ...but the owner, from their own network, signs in normally.
        login("10.4.0.2", email, "Secret123").andExpect(status().isOk());
    }

    @Test
    void credentialStuffingIsCappedPerIp() throws Exception {
        String stuffer = "10.4.1.1";
        for (int i = 0; i < 8; i++) {
            login(stuffer, "victim" + i + "-" + UUID.randomUUID() + "@example.com", "Password1")
                    .andExpect(status().isUnauthorized());
        }
        login(stuffer, "victim-next@example.com", "Password1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private String newUser() {
        String email = "lh-" + UUID.randomUUID() + "@example.com";
        userRepository.save(User.builder()
                .id(UUID.randomUUID()).email(email)
                .passwordHash(passwordEncoder.encode("Secret123"))
                .firstName("L").lastName("H").role(Role.USER).active(true).emailVerified(true)
                .build());
        return email;
    }

    private ResultActions login(String ip, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }
}
