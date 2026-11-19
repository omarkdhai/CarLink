package com.carlink.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Owner self-registration. Passwords are hashed server-side only. A valid,
 * unclaimed sticker activation code is required: accounts exist only to own a
 * sticker, so registration without one is rejected.
 *
 * <p>The password rules (length, guessability, breaches) live in
 * {@code PasswordPolicy} so every refusal gets its own error code (security
 * plan S-14); the size cap here only keeps absurd payloads away from it.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 256) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 20) @Pattern(regexp = "^$|^\\+[1-9][0-9]{6,14}$",
                message = "phone must be empty or an E.164 number starting with +")
        String phone,
        @NotBlank @Size(max = 128) String stickerCode
) {}
