package com.carlink.user.controller;

import com.carlink.auth.controller.RefreshCookie;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.ratelimit.Throttle;
import com.carlink.security.ratelimit.ThrottleProperties;
import com.carlink.user.dto.DeleteAccountRequest;
import com.carlink.user.service.AccountDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Owner self-service endpoints. Returns {@link UserResponse}, which includes
 * the owner's own private phone (serialized only to the authenticated owner —
 * never to third parties or public endpoints).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;
    private final Throttle throttle;
    private final ThrottleProperties limits;
    private final RefreshCookie refreshCookie;

    @GetMapping("/me")
    @Operation(summary = "Current user's profile (own private phone included)")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.me(requireId(principal));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update own profile (first/last name, private phone)")
    public UserResponse updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(requireId(principal), request);
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete own account and its data (password confirmation)",
            description = "Releases the owner's stickers, deletes the account with its vehicles, "
                    + "conversations and messages, ends every session and clears the refresh cookie "
                    + "(security plan S-17).")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal UserPrincipal principal,
                                              @Valid @RequestBody DeleteAccountRequest request,
                                              HttpServletRequest http, HttpServletResponse response) {
        java.util.UUID userId = requireId(principal);
        throttle.require("account-delete-user", limits.getAccountDeleteUser(), userId.toString());
        accountDeletionService.deleteOwnAccount(userId, request.password(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        refreshCookie.clear(response);
        return ResponseEntity.noContent().build();
    }

    private java.util.UUID requireId(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.id();
    }
}