package com.carlink.auth;

import com.carlink.AbstractIntegrationTest;
import com.carlink.auth.controller.RefreshCookie;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import com.carlink.notification.email.EmailSender;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
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
    @MockitoBean
    private EmailSender emailSender;

    private String capturedEmailBody;

    @BeforeEach
    void captureEmails() {
        doAnswer(inv -> {
            capturedEmailBody = inv.getArgument(2, String.class);
            return null;
        }).when(emailSender).send(anyString(), anyString(), anyString());
        // Templated emails (password reset) go through sendHtml: keep the latest
        // email's text + HTML so the token link is found whichever is used.
        doAnswer(inv -> {
            capturedEmailBody = inv.getArgument(3, String.class) + "\n" + inv.getArgument(2, String.class);
            return null;
        }).when(emailSender).sendHtml(anyString(), anyString(), anyString(), anyString());
    }

    @AfterEach
    void reset() {
        capturedEmailBody = null;
    }

    @Test
    void fullAuthLifecycleWorks() throws Exception {
        // --- Register: refresh token only in the HttpOnly cookie, never the body ---
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "owner@example.com",
                                "password", "Secret1234",
                                "firstName", "Ali",
                                "lastName", "Ben",
                                "phone", "",
                                "stickerCode", freshStickerCode()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value("owner@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andReturn();
        String setCookie = registered.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(RefreshCookie.NAME + "=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth", "Secure");
        assertThat(refreshCookieOf(registered)).isNotBlank();

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
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "owner@example.com", "password", "Secret1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asString();
        String refreshToken = refreshCookieOf(loginResult);

        // --- /me with the access token ---
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                // Privacy: the phone must never be serialized.
                .andExpect(jsonPath("$.phone").doesNotExist());

        // --- Refresh rotates the cookie ---
        MvcResult refreshed = refresh(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        String rotatedRefresh = refreshCookieOf(refreshed);
        assertThat(rotatedRefresh).isNotBlank().isNotEqualTo(refreshToken);

        // Old refresh token must no longer work (and its cookie is cleared).
        // Reuse revokes the whole family -- so log in again for the logout part.
        MvcResult reused = refresh(refreshToken).andExpect(status().isUnauthorized()).andReturn();
        assertThat(reused.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        refresh(rotatedRefresh).andExpect(status().isUnauthorized());

        MvcResult again = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "owner@example.com", "password", "Secret1234"))))
                .andExpect(status().isOk()).andReturn();
        String session = refreshCookieOf(again);

        // --- Logout revokes the token and clears the cookie ---
        MvcResult loggedOut = mockMvc.perform(post("/api/v1/auth/logout")
                        .header(RefreshCookie.CSRF_HEADER, "XMLHttpRequest")
                        .cookie(new Cookie(RefreshCookie.NAME, session)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(loggedOut.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        // That refresh token no longer refreshes either
        refresh(session).andExpect(status().isUnauthorized());
    }

    @Test
    void cookieEndpointsRequireTheCsrfHeader() throws Exception {
        // Without X-Requested-With a cross-site request is refused before the
        // cookie is even looked at (security plan S-24).
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(RefreshCookie.NAME, "anything")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(RefreshCookie.NAME, "anything")))
                .andExpect(status().isForbidden());
        // With the header but no cookie: just no session.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(RefreshCookie.CSRF_HEADER, "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationNeedsAClaimableStickerBeforeAnythingElse() throws Exception {
        // First owner registers with a code and binds that sticker to a car.
        String code = freshStickerCode();
        MvcResult first = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "first@example.com", "password", "Secret1234",
                                "firstName", "F", "lastName", "O", "phone", "", "stickerCode", code))))
                .andExpect(status().isOk()).andReturn();
        String bearer = "Bearer " + objectMapper.readTree(first.getResponse().getContentAsString())
                .get("accessToken").asString();
        mockMvc.perform(post("/api/v1/stickers/activate-by-code")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "details", Map.of(
                                "birthDate", "1990-01-01", "licensePlate", "131 TU 1313")))))
                .andExpect(status().isOk());

        // S-13: a BOUND sticker no longer opens registration.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "second@example.com", "password", "Secret1234",
                                "firstName", "S", "lastName", "O", "phone", "", "stickerCode", code))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STICKER_ALREADY_BOUND"));

        // S-13: without a valid code, a registered e-mail is not revealed.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "first@example.com", "password", "Secret1234",
                                "firstName", "F", "lastName", "O", "phone", "",
                                "stickerCode", "ZZZZ-ZZZZ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STICKER_CODE"));
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
                                "password", "Secret1234",
                                "firstName", "X",
                                "lastName", "Y",
                                "phone", "",
                                "stickerCode", freshStickerCode()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "who@example.com",
                                "password", "WrongPass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void aNewResetLinkVoidsTheOlderOnes() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "relink@example.com", "password", "Secret1234",
                                "firstName", "R", "lastName", "L", "phone", "",
                                "stickerCode", freshStickerCode()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "relink@example.com"))))
                .andExpect(status().isOk());
        String first = extractToken(capturedEmailBody);
        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "relink@example.com"))))
                .andExpect(status().isOk());
        String second = extractToken(capturedEmailBody);
        assertThat(second).isNotEqualTo(first);

        // S-21: the first link died when the second was sent.
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", first, "newPassword", "Another-Pass-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_LINK"));
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", second, "newPassword", "Another-Pass-1"))))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetFlowUpdatesPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "reset@example.com",
                                "password", "Secret1234",
                                "firstName", "A",
                                "lastName", "B",
                                "phone", "",
                                "stickerCode", freshStickerCode()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "reset@example.com"))))
                .andExpect(status().isOk());

        String resetToken = extractToken(capturedEmailBody);

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", resetToken,
                                "newPassword", "BrandNew123"))))
                .andExpect(status().isOk());

        // Old password fails, new password works
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "reset@example.com",
                                "password", "Secret1234"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "reset@example.com",
                                "password", "BrandNew123"))))
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

    private org.springframework.test.web.servlet.ResultActions refresh(String rawRefresh) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .header(RefreshCookie.CSRF_HEADER, "XMLHttpRequest")
                .cookie(new Cookie(RefreshCookie.NAME, rawRefresh)));
    }

    private static String refreshCookieOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshCookie.NAME);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie.getValue();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** Orders a SINGLE sticker as a guest and returns the printed activation code. */
    private String freshStickerCode() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "items", List.of(Map.of("stickerPackage", "SINGLE", "quantity", 1)),
                                "customerName", "Test Buyer",
                                "mobile", "+21655123456",
                                "email", "buyer@example.com",
                                "governorate", "TUNIS",
                                "deliveryAddress", "1 Place de la République"))))
                .andExpect(status().isOk())
                .andReturn();
        return issuedStickers(objectMapper.readTree(r.getResponse().getContentAsString()))
                .get(0).get("activationCode").asString();
    }
}