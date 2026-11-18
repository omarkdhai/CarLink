package com.carlink.security.captcha;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, non-secret runtime configuration for the SPA, so one frontend build
 * works in every environment.
 */
@RestController
@RequestMapping("/api/v1/public/config")
@RequiredArgsConstructor
@Tag(name = "Public config")
public class PublicConfigController {

    private final CaptchaProperties captcha;

    public record PublicConfig(String captchaSiteKey) {
    }

    @GetMapping
    @Operation(summary = "Public client configuration (captcha site key when bot protection is on)")
    public PublicConfig config() {
        return new PublicConfig(captcha.isEnabled() ? captcha.getSiteKey() : null);
    }
}
