package com.carlink.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Production sanity checks on the notification providers (security plan S-16).
 *
 * <ul>
 *   <li><b>Email:</b> the mock sender delivers nothing — verification and
 *   password-reset links would silently vanish — so the prod profile refuses to
 *   start with it.</li>
 *   <li><b>Contact relay:</b> production may legitimately run on the mock relay
 *   until the SMS provider is configured. That is allowed, but announced loudly, and the
 *   mock never logs message content.</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionProviderGuard {

    private final CarLinkProperties properties;
    private final com.carlink.security.captcha.CaptchaProperties captcha;

    @PostConstruct
    void check() {
        if (isMock(properties.email().provider())) {
            throw new IllegalStateException(
                    "EMAIL_PROVIDER=mock is not allowed in the prod profile: verification and "
                            + "password-reset emails would never be delivered. Set EMAIL_PROVIDER=smtp "
                            + "and the MAIL_* variables.");
        }
        String from = properties.email().from();
        if (from == null || from.isBlank() || from.trim().endsWith(".invalid")) {
            // S-22: no committed default sender any more; prod must name one.
            throw new IllegalStateException("MAIL_FROM is not set: production needs a real sender "
                    + "address the SMTP account may send as.");
        }
        if (!captcha.isEnabled()) {
            log.warn("CAPTCHA_ENABLED=false in production: public forms (orders, alerts, contact, "
                    + "reports) are protected by rate limits only. Configure Turnstile (security plan S-11).");
        } else if (captcha.getSecretKey() == null || captcha.getSecretKey().isBlank()) {
            throw new IllegalStateException("CAPTCHA_ENABLED=true but CAPTCHA_SECRET_KEY is empty.");
        }
        if (isMock(properties.contact().provider())) {
            log.warn("CONTACT_PROVIDER=mock in production: visitor alerts are recorded but NOT delivered "
                    + "to owners. Configure CONTACT_PROVIDER=tunisiesms and TUNISIESMS_API_KEY.");
        }
    }

    private static boolean isMock(String provider) {
        return provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider.trim());
    }
}
