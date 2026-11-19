package com.carlink.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** New password via an e-mailed reset token; rules in {@code PasswordPolicy} (S-14). */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(max = 256) String newPassword
) {}
