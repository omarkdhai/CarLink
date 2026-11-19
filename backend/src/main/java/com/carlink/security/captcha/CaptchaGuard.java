package com.carlink.security.captcha;

import com.carlink.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Enforces the captcha on a public form. The SPA sends the Turnstile response
 * in the {@value #HEADER} header (no DTO changes, never logged by nginx).
 */
@Component
@RequiredArgsConstructor
public class CaptchaGuard {

    public static final String HEADER = "X-Captcha-Token";

    private final CaptchaVerifier verifier;

    /** True when Turnstile is configured, i.e. a challenge can actually be shown. */
    public boolean isEnabled() {
        return verifier.isEnabled();
    }

    /**
     * Step-up check (security plan S-11): answers 400 CAPTCHA_REQUIRED unless the
     * token verifies. Distinct from CAPTCHA_FAILED so the SPA knows to show the
     * widget on a form that normally has none (login). Only call when enabled.
     */
    public void requireChallenge(String token, String clientIp) {
        if (!verifier.verify(token, clientIp)) {
            throw new BadRequestException("CAPTCHA_REQUIRED",
                    "Please confirm you are not a robot to continue.");
        }
    }

    /** Answers 400 CAPTCHA_FAILED unless the token verifies (or captcha is off). */
    public void require(String token, String clientIp) {
        if (!verifier.verify(token, clientIp)) {
            throw new BadRequestException("CAPTCHA_FAILED",
                    "Please confirm you are not a robot and try again.");
        }
    }
}
