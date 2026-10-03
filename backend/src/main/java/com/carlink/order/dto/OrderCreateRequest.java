package com.carlink.order.dto;

import com.carlink.order.model.Governorate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Guest checkout form for a COD sticker order. Mobile must be E.164;
 * governorate is one of the 24 Tunisian governorates. Prices and sticker
 * counts are resolved entirely server-side.
 */
public record OrderCreateRequest(
        @NotEmpty @Valid List<OrderItemRequest> items,
        @NotBlank @Size(max = 100) String customerName,
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^\\+[1-9][0-9]{6,14}$",
                message = "mobile must be an E.164 number starting with +")
        String mobile,
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull Governorate governorate,
        @NotBlank @Size(max = 255) String deliveryAddress,
        @Size(max = 500) String deliveryNotes
) {}
