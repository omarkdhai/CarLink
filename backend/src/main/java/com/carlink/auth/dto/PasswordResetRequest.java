package com.carlink.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to email a password-reset link. Responds identically whether or not
 * the email exists (anti-enumeration).
 */
public record PasswordResetRequest(
        @NotBlank @Email String email
) {}