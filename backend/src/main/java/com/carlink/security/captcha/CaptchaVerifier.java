package com.carlink.security.captcha;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Verifies a Cloudflare Turnstile response token server-side.
 *
 * <p>Fails closed: a network error or an unexpected answer counts as "not
 * verified" (the visitor can simply retry), so an outage at the provider never
 * silently disables the protection.
 */
@Slf4j
@Component
public class CaptchaVerifier {

    private final CaptchaProperties properties;
    private final RestClient http;

    public CaptchaVerifier(CaptchaProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.http = builder.build();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** True when captcha is disabled, or when Turnstile confirms the token. */
    public boolean verify(String responseToken, String clientIp) {
        if (!properties.isEnabled()) {
            return true;
        }
        if (responseToken == null || responseToken.isBlank() || responseToken.length() > 2048) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.getSecretKey());
        form.add("response", responseToken);
        if (clientIp != null && !clientIp.isBlank()) {
            form.add("remoteip", clientIp);
        }
        try {
            SiteVerify result = http.post()
                    .uri(properties.getVerifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SiteVerify.class);
            if (result == null || !result.success()) {
                log.info("Captcha rejected (codes={})", result == null ? "none" : result.errorCodes());
                return false;
            }
            return true;
        } catch (RestClientException e) {
            log.warn("Captcha verification unavailable: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    record SiteVerify(boolean success, @JsonProperty("error-codes") List<String> errorCodes) {
    }
}
