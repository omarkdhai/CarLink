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

    /** Answers 400 CAPTCHA_FAILED unless the token verifies (or captcha is off). */
    public void require(String token, String clientIp) {
        if (!verifier.verify(token, clientIp)) {
            throw new BadRequestException("CAPTCHA_FAILED",
                    "Please confirm you are not a robot and try again.");
        }
    }
}
