package com.carlink.security.captcha;

import com.carlink.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security plan S-11 follow-up, with Turnstile on: once one account has failed
 * logins from many IPs, its login needs a captcha (400 CAPTCHA_REQUIRED until
 * a valid token comes with it).
 */
@TestPropertySource(properties = {
        "carlink.throttle.login-account-failures.max=2",
        "carlink.throttle.login-account-failures.window-seconds=900",
})
class LoginChallengeIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger IP = new AtomicInteger(1);

    @MockitoBean
    private CaptchaVerifier verifier;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void turnstileOn() {
        when(verifier.isEnabled()).thenReturn(true);
        when(verifier.verify(eq("good-token"), any())).thenReturn(true);
    }

    @Test
    void aGuessedAccountNeedsACaptchaToTryAgain() throws Exception {
        String email = "challenged-" + UUID.randomUUID() + "@example.com";
        // Below the threshold no captcha is asked for (login has no widget by default).
        mockMvc.perform(login(email)).andExpect(status().isUnauthorized());
        mockMvc.perform(login(email)).andExpect(status().isUnauthorized());

        mockMvc.perform(login(email))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
        mockMvc.perform(login(email).header(CaptchaGuard.HEADER, "bad-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
        // With a valid token the attempt goes through to the password check.
        mockMvc.perform(login(email).header(CaptchaGuard.HEADER, "good-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    private MockHttpServletRequestBuilder login(String email) throws Exception {
        String ip = "10.12.0." + IP.getAndIncrement();
        return post("/api/v1/auth/login")
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Wrong-pass-123")));
    }
}
