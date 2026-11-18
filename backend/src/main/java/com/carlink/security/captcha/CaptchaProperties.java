package com.carlink.security.captcha;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bot protection for the public forms (security plan S-11), backed by
 * Cloudflare Turnstile. Off unless {@code CAPTCHA_ENABLED=true}; the site key is
 * public (served to the SPA), the secret key never leaves the backend.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "carlink.captcha")
public class CaptchaProperties {

    /** When false, every check passes and the SPA renders no widget. */
    private boolean enabled = false;
    /** Public site key rendered by the widget. */
    private String siteKey = "";
    /** Server-side secret used for siteverify. */
    private String secretKey = "";
    private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
}
