package com.carlink.admin.dto;

import com.carlink.user.model.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin-side view of a user. Deliberately excludes {@code phone} — the
 * owner's number is private and never returned by any API, admin included.
 */
public record AdminUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean active,
        boolean emailVerified,
        Instant createdAt,
        long vehicleCount
) {

    public static AdminUserResponse from(User user, long vehicleCount) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name(),
                user.isActive(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                vehicleCount);
    }
}