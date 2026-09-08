package com.carlink.auth;

import com.carlink.AbstractIntegrationTest;
import com.carlink.notification.email.EmailSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth flow against real PostgreSQL + Redis (Testcontainers):
 * register → verify email → login → refresh → /me → logout.
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern TOKEN_IN_LINK =
            Pattern.compile("token=([A-Za-z0-9._~-]+)");

    @Autowired
    private ObjectMapper objectMapper;

    /** Replaces the dev LogEmailSender so emails are captured instead of logged. */
    @MockBean
    private EmailSender emailSender;

    private String capturedEmailBody;

    @BeforeEach
    void captureEmails() {
        doAnswer(inv -> {
            capturedEmailBody = inv.getArgument(2, String.class);
            return null;
        }).when(emailSender).send(anyString(), anyString(), anyString());
    }

    @AfterEach
    void reset() {
        capturedEmailBody = null;
    }

    @Test
    void fullAuthLifecycleWorks() throws Exception {
        // --- Register ---
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "owner@example.com",
                                "password", "Secret123",
                                "firstName", "Ali",
                                "lastName", "Ben",
                                "phone", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("owner@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andReturn();

        String refreshToken = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // A verification link was "emailed"
        verify(emailSender).send(anyString(), anyString(), anyString());
        String verificationToken = extractToken(capturedEmailBody);

        // --- Verify email ---
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", verificationToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified"));

        // --- Login ---
        JsonNode login = postJson("/api/v1/auth/login",
                Map.of("email", "owner@example.com", "password", "Secret123"));
        String accessToken = login.get("accessToken").asText();
        refreshToken = login.get("refreshToken").asText();

        // --- /me with the access token ---
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                // Privacy: the phone must never be serialized.
                .andExpect(jsonPath("$.phone").doesNotExist());

        // --- Refresh rotates the session ---
        JsonNode refreshed = postJson("/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken));
        String rotatedRefresh = refreshed.get("refreshToken").asText();
        assertThat(rotatedRefresh).isNotEqualTo(refreshToken);

        // Old refresh token must no longer work
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());

        // --- Logout revokes the rotated token ---
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", rotatedRefresh))))
                .andExpect(status().isOk());

        // That refresh token no longer refreshes either
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", rotatedRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequestGets401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void wrongPasswordGetsGenericMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "who@example.com",
                                "password", "Secret123",
                                "firstName", "X",
                                "lastName", "Y",
                                "phone", ""))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "who@example.com",
                                "password", "WrongPass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void passwordResetFlowUpdatesPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "reset@example.com",
                                "password", "Secret123",
                                "firstName", "A",
                                "lastName", "B",
                                "phone", ""))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "reset@example.com"))))
                .andExpect(status().isOk());

        String resetToken = extractToken(capturedEmailBody);

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", resetToken,
                                "newPassword", "BrandNew1"))))
                .andExpect(status().isOk());

        // Old password fails, new password works
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "reset@example.com",
                                "password", "Secret123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "reset@example.com",
                                "password", "BrandNew1"))))
                .andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private String extractToken(String emailBody) {
        Matcher m = TOKEN_IN_LINK.matcher(emailBody);
        assertThat(m.find())
                .as("email body should contain a token link, was: %s", emailBody)
                .isTrue();
        return m.group(1);
    }

    private JsonNode postJson(String url, Map<String, String> body) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}