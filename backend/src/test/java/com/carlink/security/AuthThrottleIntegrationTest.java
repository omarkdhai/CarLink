package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import com.carlink.auth.service.JwtService;
import com.carlink.notification.email.EmailSender;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security plan S-03: every authentication / activation endpoint is throttled.
 * Limits are set to 2 here; each test uses its own client IP so they cannot
 * interfere with each other (the Redis container is shared by the suite).
 */
@TestPropertySource(properties = {
        "carlink.throttle.register-ip.max=2",
        "carlink.throttle.password-reset-ip.max=2",
        "carlink.throttle.password-reset-email.max=1",
        "carlink.throttle.password-reset-confirm-ip.max=2",
        "carlink.throttle.verify-email-ip.max=2",
        "carlink.throttle.refresh-ip.max=2",
        "carlink.throttle.sticker-activate-user.max=2",
        "carlink.throttle.sticker-activate-ip.max=100",
})
class AuthThrottleIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private EmailSender emailSender;

    @Test
    void registerIsThrottledPerIp() throws Exception {
        String ip = nextIp();
        Map<String, String> body = Map.of("email", "t" + UUID.randomUUID() + "@example.com",
                "password", "Secret123", "firstName", "A", "lastName", "B", "phone", "",
                "stickerCode", "ZZZZ-ZZZZ");
        call(post("/api/v1/auth/register"), ip, body).andExpect(status().isBadRequest());
        call(post("/api/v1/auth/register"), ip, body).andExpect(status().isBadRequest());
        call(post("/api/v1/auth/register"), ip, body)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
        // Another client is unaffected.
        call(post("/api/v1/auth/register"), nextIp(), body).andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetIsThrottledPerIp() throws Exception {
        String ip = nextIp();
        Map<String, String> body = Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com");
        call(post("/api/v1/auth/password-reset"), ip, body).andExpect(status().isOk());
        call(post("/api/v1/auth/password-reset"), ip, body).andExpect(status().isOk());
        call(post("/api/v1/auth/password-reset"), ip, body).andExpect(status().isTooManyRequests());
    }

    @Test
    void passwordResetMailsAreCappedPerAddressSilently() throws Exception {
        String email = "victim-" + UUID.randomUUID() + "@example.com";
        userRepository.save(User.builder()
                .id(UUID.randomUUID()).email(email)
                .passwordHash(passwordEncoder.encode("Secret123"))
                .firstName("V").lastName("W").role(Role.USER).active(true).emailVerified(true)
                .build());

        // Two requests from two IPs: both get the same 200, only one mail is sent.
        call(post("/api/v1/auth/password-reset"), nextIp(), Map.of("email", email)).andExpect(status().isOk());
        call(post("/api/v1/auth/password-reset"), nextIp(), Map.of("email", email)).andExpect(status().isOk());
        verify(emailSender, times(1)).sendHtml(eq(email), anyString(), anyString(), anyString());
    }

    @Test
    void resetConfirmVerifyAndRefreshAreThrottledPerIp() throws Exception {
        assertThrottled("/api/v1/auth/password-reset/confirm", Map.of("token", "nope", "newPassword", "Secret123"));
        assertThrottled("/api/v1/auth/verify-email", Map.of("token", "nope"));
        assertThrottled("/api/v1/auth/refresh", Map.of());
    }

    @Test
    void stickerActivationIsThrottledPerAccount() throws Exception {
        String bearer = "Bearer " + jwtService.issueAccessToken(User.builder()
                .id(UUID.randomUUID()).email("guess@example.com").role(Role.USER).build());
        Map<String, Object> body = Map.of("code", "ZZZZ-ZZZZ",
                "details", Map.of("licensePlate", "123 TU 4567", "birthDate", "1990-01-01"));
        for (int i = 0; i < 2; i++) {
            call(post("/api/v1/stickers/activate-by-code").header("Authorization", bearer), nextIp(), body)
                    .andExpect(result -> {
                        if (result.getResponse().getStatus() == 429) throw new AssertionError("throttled too early");
                    });
        }
        // Third guess from yet another IP: the per-account limit still applies.
        call(post("/api/v1/stickers/activate-by-code").header("Authorization", bearer), nextIp(), body)
                .andExpect(status().isTooManyRequests());
    }

    // ---------- helpers ----------

    private void assertThrottled(String url, Map<String, String> body) throws Exception {
        String ip = nextIp();
        for (int i = 0; i < 2; i++) {
            call(post(url), ip, body).andExpect(result -> {
                if (result.getResponse().getStatus() == 429) throw new AssertionError(url + " throttled too early");
            });
        }
        call(post(url), ip, body).andExpect(status().isTooManyRequests());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String ip, Object body) throws Exception {
        return mockMvc.perform(request
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static String nextIp() {
        int n = IP.getAndIncrement();
        return "10.3." + (n / 250) + "." + (n % 250 + 1);
    }
}
