package com.carlink.user.controller;

import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import com.carlink.user.dto.UpdateProfileRequest;
import com.carlink.user.model.UserResponse;
import com.carlink.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner self-service endpoints. Returns {@link UserResponse} only — the private
 * phone number is never serialized.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Current user's profile (no phone number is returned)")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.me(requireId(principal));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update own profile (first/last name, private phone)")
    public UserResponse updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(requireId(principal), request);
    }

    private java.util.UUID requireId(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.id();
    }
}