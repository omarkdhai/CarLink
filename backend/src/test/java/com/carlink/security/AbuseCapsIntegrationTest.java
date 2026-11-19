package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security plan S-11 follow-ups, with Turnstile off: a daily order cap per
 * e-mail and per mobile, and a per-account slow-down once one account has
 * failed logins from many IPs. The owner is never locked out.
 */
@TestPropertySource(properties = {
        "carlink.throttle.order-email-day.max=2",
        "carlink.throttle.order-email-day.window-seconds=86400",
        "carlink.throttle.order-mobile-day.max=2",
        "carlink.throttle.order-mobile-day.window-seconds=86400",
        "carlink.throttle.login-account-failures.max=3",
        "carlink.throttle.login-account-failures.window-seconds=900",
        "carlink.throttle.login-account-slow.max=1",
        "carlink.throttle.login-account-slow.window-seconds=30",
})
class AbuseCapsIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void ordersAreCappedPerEmailPerDay() throws Exception {
        String email = "cap-" + UUID.randomUUID() + "@example.com";
        order(email, "+21650000001").andExpect(status().isOk());
        order(email, "+21650000002").andExpect(status().isOk());
        order(email, "+21650000003")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ORDER_LIMIT_REACHED"));
    }

    @Test
    void ordersAreCappedPerMobilePerDay() throws Exception {
        String mobile = "+2165" + (1000000 + IP.incrementAndGet());
        order("m1-" + UUID.randomUUID() + "@example.com", mobile).andExpect(status().isOk());
        order("m2-" + UUID.randomUUID() + "@example.com", mobile).andExpect(status().isOk());
        order("m3-" + UUID.randomUUID() + "@example.com", mobile)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ORDER_LIMIT_REACHED"));
    }

    @Test
    void distributedGuessingSlowsTheAccountButNeverLocksTheOwnerOut() throws Exception {
        String email = "guessed-" + UUID.randomUUID() + "@example.com";
        userRepository.save(User.builder().id(UUID.randomUUID()).email(email)
                .passwordHash(passwordEncoder.encode("Secret1234")).role(Role.USER)
                .active(true).emailVerified(true).build());

        // Three wrong passwords from three addresses: the per-IP lockout never trips.
        for (int i = 0; i < 3; i++) {
            login(email, "Wrong-pass-" + i).andExpect(status().isUnauthorized());
        }
        // The account is now challenged; with Turnstile off it is slowed down:
        // one attempt per window, whoever sends it.
        login(email, "Wrong-pass-x").andExpect(status().isUnauthorized());
        login(email, "Wrong-pass-y")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_SLOWED_DOWN"));
    }

    @Test
    void theOwnerStillGetsInAndThatEndsTheChallenge() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        userRepository.save(User.builder().id(UUID.randomUUID()).email(email)
                .passwordHash(passwordEncoder.encode("Secret1234")).role(Role.USER)
                .active(true).emailVerified(true).build());
        for (int i = 0; i < 3; i++) {
            login(email, "Wrong-pass-" + i).andExpect(status().isUnauthorized());
        }
        login(email, "Secret1234").andExpect(status().isOk());
        // Success cleared the account counter: no slow-down afterwards.
        login(email, "Secret1234").andExpect(status().isOk());
        login(email, "Secret1234").andExpect(status().isOk());
    }

    private ResultActions login(String email, String password) throws Exception {
        String ip = nextIp();
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private ResultActions order(String email, String mobile) throws Exception {
        String ip = nextIp();
        return mockMvc.perform(post("/api/v1/public/orders")
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "items", List.of(Map.of("stickerPackage", "SINGLE", "quantity", 1)),
                        "customerName", "Cap Test", "mobile", mobile, "email", email,
                        "governorate", "TUNIS", "deliveryAddress", "1 Rue du Test"))));
    }

    private static String nextIp() {
        int n = IP.getAndIncrement();
        return "10.11." + (n / 250) + "." + (n % 250 + 1);
    }
}
