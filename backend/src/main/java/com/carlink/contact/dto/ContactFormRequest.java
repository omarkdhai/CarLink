package com.carlink.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for the public "Contact us" form. The visitor reaches the business
 * mailbox; their email is only used to thread the reply, never stored or shared.
 */
public record ContactFormRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 150) String subject,
        @Size(max = 30) String phone,
        @NotBlank @Size(max = 2000) String message
) {}