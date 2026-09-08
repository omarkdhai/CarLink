package com.carlink.auth.controller;

import com.carlink.auth.dto.AuthResponse;
import com.carlink.auth.dto.LoginRequest;
import com.carlink.auth.dto.PasswordResetRequest;
import com.carlink.auth.dto.RefreshRequest;
import com.carlink.auth.dto.RegisterRequest;
import com.carlink.auth.dto.ResetPasswordRequest;
import com.carlink.auth.dto.VerifyEmailRequest;
import com.carlink.auth.service.AuthService;
import com.carlink.common.dto.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (unauthenticated) authentication endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new owner account")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm email address using the emailed token")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return MessageResponse.of("Email verified");
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive an access + refresh token pair")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and receive a fresh token pair")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the presented refresh token (ends the session)")
    public ResponseEntity<MessageResponse> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.ok(MessageResponse.of("Logged out"));
    }

    @PostMapping("/password-reset")
    @Operation(summary = "Request a password-reset link (sent by email)")
    public ResponseEntity<MessageResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok(
                MessageResponse.of("If the email exists, a reset link has been sent."));
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Set a new password using the emailed reset token")
    public MessageResponse confirmPasswordReset(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return MessageResponse.of("Password updated");
    }
}