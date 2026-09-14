package com.carlink.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root configuration properties for the module. Binds the `carlink.*` prefix.
 */
@ConfigurationProperties(prefix = "carlink")
public record CarLinkProperties(
        String baseUrl,
        String publicUrl,
        Jwt jwt,
        Conversation conversation,
        Qr qr,
        RateLimit ratelimit,
        Contact contact,
        ContactForm contactForm,
        Email email,
        Security security,
        Admin admin
) {

    public record Jwt(
            String secret,
            long accessExpirationMinutes,
            String refreshSecret,
            long refreshExpirationDays
    ) {}

    public record Conversation(long expiryHours) {}

    public record Qr(int tokenBytes) {}

    public record RateLimit(
            int ipPerMinute,
            int qrPerHour,
            String captureHosts,
            int maxLoginFailures,
            int loginLockMinutes,
            int reportIpPerMinute
    ) {}

    public record Contact(String provider) {}

    /**
     * Public "Contact us" form: the mailbox the form messages are delivered to
     * and the per-IP per-minute rate limit (guarded before any send).
     */
    public record ContactForm(String toEmail, int ipPerMinute) {}

    /** Email sending strategy: {@code mock} (logs, dev) or {@code smtp}. */
    public record Email(String provider, long tokenExpiryMinutes) {}

    public record Security(String corsAllowedOrigins) {}

    /**
     * Initial ADMIN bootstrap. When {@code bootstrapEmail} is set and no user
     * with that email exists, the app creates that user with the ADMIN role on
     * startup. Left unset in production; only enabled for dev management.
     */
    public record Admin(String bootstrapEmail, String bootstrapPassword) {}
}