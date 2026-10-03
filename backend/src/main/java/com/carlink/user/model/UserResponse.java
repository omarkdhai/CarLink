package com.carlink.user.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner's own profile payload. {@code phone} is serialized ONLY to the
 * authenticated owner themselves (login / refresh / {@code /users/me}) so
 * they can verify the number used to route SMS and anonymous-call relay.
 * It is never exposed to third parties, public/unauthenticated APIs, HTML,
 * QR codes, URLs or logs. See {@code docs/security.md}.
 */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean emailVerified,
        Instant createdAt,
        String phone
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getPhone());
    }
}