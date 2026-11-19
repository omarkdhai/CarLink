package com.carlink.security.captcha;

import com.carlink.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security plan S-11: with bot protection on, public forms need a verified token. */
@TestPropertySource(properties = {
        "carlink.captcha.enabled=true",
        "carlink.captcha.site-key=public-site-key",
        "carlink.captcha.secret-key=server-secret",
})
class CaptchaIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private CaptchaVerifier verifier; // unanswered calls return false = not verified

    private static final String CONTACT_FORM = """
            {"name":"Ali","email":"ali@example.com","subject":"Hi","message":"Hello there"}""";

    @Test
    void exposesTheSiteKeyButNeverTheSecret() throws Exception {
        mockMvc.perform(get("/api/v1/public/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captchaSiteKey").value("public-site-key"))
                .andExpect(jsonPath("$.secretKey").doesNotExist());
    }

    @Test
    void contactFormWithoutAVerifiedTokenIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/public/contact")
                        .contentType(MediaType.APPLICATION_JSON).content(CONTACT_FORM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTCHA_FAILED"));
    }

    @Test
    void contactFormWithAVerifiedTokenGoesThrough() throws Exception {
        when(verifier.verify(eq("good-token"), any())).thenReturn(true);
        mockMvc.perform(post("/api/v1/public/contact")
                        .header(CaptchaGuard.HEADER, "good-token")
                        .contentType(MediaType.APPLICATION_JSON).content(CONTACT_FORM))
                .andExpect(status().isOk());
    }

    @Test
    void ordersAndAlertsAreGuardedToo() throws Exception {
        mockMvc.perform(post("/api/v1/public/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"items":[{"stickerPackage":"SINGLE","quantity":1}],"customerName":"A B",
                         "mobile":"+21655123456","email":"a@example.com","governorate":"TUNIS",
                         "deliveryAddress":"1 Rue X"}"""))
                .andExpect(jsonPath("$.code").value("CAPTCHA_FAILED"));
        mockMvc.perform(post("/api/v1/public/qr/contact").header("X-CarLink-Token", "whatever")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"SMS\",\"reason\":\"BLOCKING\"}"))
                .andExpect(jsonPath("$.code").value("CAPTCHA_FAILED"));
        mockMvc.perform(post("/api/v1/public/qr/report").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"00000000-0000-0000-0000-000000000001\",\"reason\":\"SPAM\"}"))
                .andExpect(jsonPath("$.code").value("CAPTCHA_FAILED"));
    }
}
