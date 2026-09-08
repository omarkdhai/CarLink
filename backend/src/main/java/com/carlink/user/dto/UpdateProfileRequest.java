package com.carlink.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Profile fields that can be updated by the owner themselves.
 * {@code phone} is accepted on profile edit (it is needed to route
 * notifications) but is never echoed back to any client.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 20) @Pattern(regexp = "^$|^\\+[1-9][0-9]{6,14}$",
                message = "phone must be empty or an E.164 number starting with +")
        String phone
) {}