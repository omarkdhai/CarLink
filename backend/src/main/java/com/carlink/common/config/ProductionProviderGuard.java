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
 *   until Telnyx is configured. That is allowed, but announced loudly, and the
 *   mock never logs message content.</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionProviderGuard {

    private final CarLinkProperties properties;

    @PostConstruct
    void check() {
        if (isMock(properties.email().provider())) {
            throw new IllegalStateException(
                    "EMAIL_PROVIDER=mock is not allowed in the prod profile: verification and "
                            + "password-reset emails would never be delivered. Set EMAIL_PROVIDER=smtp "
                            + "and the MAIL_* variables.");
        }
        if (isMock(properties.contact().provider())) {
            log.warn("CONTACT_PROVIDER=mock in production: visitor alerts are recorded but NOT delivered "
                    + "to owners. Configure CONTACT_PROVIDER=telnyx and the TELNYX_* variables.");
        }
    }

    private static boolean isMock(String provider) {
        return provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider.trim());
    }
}
