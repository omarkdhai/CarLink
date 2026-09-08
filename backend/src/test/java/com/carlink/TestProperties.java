package com.carlink;

import com.carlink.common.config.CarLinkProperties;

/**
 * Factory for a minimal validated {@link CarLinkProperties} used by unit tests.
 */
public final class TestProperties {

    private TestProperties() {
    }

    public static CarLinkProperties minimal() {
        return new CarLinkProperties(
                "http://localhost:8080",
                "http://localhost:4200",
                new CarLinkProperties.Jwt(
                        "unit-test-secret-key-at-least-32-characters-long!!",
                        15,
                        "unit-test-refresh-secret-at-least-32-chars!!",
                        7),
                new CarLinkProperties.Conversation(24),
                new CarLinkProperties.Qr(32),
                new CarLinkProperties.RateLimit(5, 20, "https://captcha.invalid", 5, 15),
                new CarLinkProperties.Contact("mock"),
                new CarLinkProperties.Email("mock", 60),
                new CarLinkProperties.Security("http://localhost:4200"));
    }
}