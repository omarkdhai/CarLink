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
        Email email,
        Security security
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
            int loginLockMinutes
    ) {}

    public record Contact(String provider) {}

    /** Email sending strategy: {@code mock} (logs, dev) or {@code smtp}. */
    public record Email(String provider, long tokenExpiryMinutes) {}

    public record Security(String corsAllowedOrigins) {}
}