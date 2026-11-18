package com.carlink.auth.dto;

import com.carlink.user.model.UserResponse;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Token pair returned after login / refresh / registration.
 *
 * <p>Only the access token and the user reach the JSON body. The refresh token
 * rides here from the service to the controller, which puts it in the HttpOnly
 * {@code carlink_rt} cookie (security plan S-09); {@code @JsonIgnore} keeps it
 * out of every serialised response. Only its hash is stored server-side.
 */
public record AuthResponse(
        String accessToken,
        @JsonIgnore String refreshToken,
        long expiresIn,
        UserResponse user
) {}
