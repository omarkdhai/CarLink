package com.carlink.auth.dto;

import com.carlink.user.model.UserResponse;

/**
 * Token pair returned after login / refresh / registration confirmation.
 * The refresh token is returned once; only its hash is stored server-side.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserResponse user
) {}