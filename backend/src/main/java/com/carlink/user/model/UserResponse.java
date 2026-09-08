package com.carlink.user.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Public user payload. Deliberately excludes {@code phone} — the owner's
 * number is private and never returned by any API.
 */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean emailVerified,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }
}