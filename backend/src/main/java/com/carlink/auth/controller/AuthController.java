package com.carlink.auth.controller;

import com.carlink.security.ratelimit.Throttle;
import com.carlink.security.ratelimit.ThrottleProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.carlink.auth.dto.AuthResponse;
import com.carlink.auth.dto.LoginRequest;
import com.carlink.auth.dto.PasswordResetRequest;
import com.carlink.auth.dto.RegisterRequest;
import com.carlink.auth.dto.ResetPasswordRequest;
import com.carlink.auth.dto.VerifyEmailRequest;
import com.carlink.auth.service.AuthService;
import com.carlink.auth.service.LoginAttemptService;
import com.carlink.security.captcha.CaptchaGuard;
import org.springframework.web.bind.annotation.RequestHeader;
import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
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
 *
 * <p>The refresh token never appears in a request or response body: it lives
 * in the HttpOnly {@code carlink_rt} cookie (security plan S-09, see
 * {@link RefreshCookie}).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final Throttle throttle;
    private final ThrottleProperties limits;
    private final RefreshCookie refreshCookie;
    private final LoginAttemptService loginAttemptService;
    private final CaptchaGuard captchaGuard;

    @PostMapping("/register")
    @Operation(summary = "Register a new owner account")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request,
                                 HttpServletRequest http, HttpServletResponse response) {
        // S-03: also bounds sticker-code guessing, since registration validates the code.
        throttle.require("register-ip", limits.getRegisterIp(), http.getRemoteAddr());
        return withCookie(authService.register(request), response);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm email address using the emailed token")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request,
                                       HttpServletRequest http) {
        throttle.require("verify-email-ip", limits.getVerifyEmailIp(), http.getRemoteAddr());
        authService.verifyEmail(request.token());
        return MessageResponse.of("Email verified");
    }

    @PostMapping("/login")
    @Operation(summary = "Log in: access token in the body, refresh token in an HttpOnly cookie")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                              @RequestHeader(value = CaptchaGuard.HEADER, required = false) String captcha,
                              HttpServletResponse response) {
        // S-04: one client may try only so many logins, whatever the e-mail
        // (credential stuffing); per-(IP, e-mail) lockout lives in AuthService.
        String ip = http.getRemoteAddr();
        throttle.require("login-ip", limits.getLoginIp(), ip);

        // S-11: many failures on ONE account from many IPs (distributed guessing)
        // step its login up to a captcha. Never a lock, so nobody can shut the
        // owner out; without Turnstile configured, the account is slowed instead.
        // Same answer for unknown e-mails: this reveals nothing about accounts.
        if (loginAttemptService.isAccountChallenged(request.email(), limits.getLoginAccountFailures())) {
            if (captchaGuard.isEnabled()) {
                captchaGuard.requireChallenge(captcha, ip);
            } else {
                throttle.require("login-account-slow", limits.getLoginAccountSlow(), request.email(),
                        "LOGIN_SLOWED_DOWN",
                        "Too many failed sign-ins on this account. Wait a moment and try again.");
            }
        }
        try {
            return withCookie(authService.login(request, ip), response);
        } catch (UnauthorizedException e) {
            if ("INVALID_CREDENTIALS".equals(e.getCode())) {
                loginAttemptService.recordAccountFailure(request.email(), limits.getLoginAccountFailures());
            }
            throw e;
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh-token cookie and receive a fresh access token",
            description = "The refresh token travels only in the HttpOnly carlink_rt cookie "
                    + "(security plan S-09); the call must carry the X-Requested-With header.")
    public AuthResponse refresh(HttpServletRequest http, HttpServletResponse response) {
        refreshCookie.requireCsrfHeader(http);
        throttle.require("refresh-ip", limits.getRefreshIp(), http.getRemoteAddr());
        String raw = refreshCookie.read(http);
        if (raw == null) {
            throw new UnauthorizedException("Invalid or expired session");
        }
        try {
            return withCookie(authService.refresh(raw), response);
        } catch (UnauthorizedException e) {
            // A dead cookie is useless to keep: drop it so the browser stops sending it.
            refreshCookie.clear(response);
            throw e;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the session's refresh token and clear its cookie")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest http,
                                                  HttpServletResponse response) {
        refreshCookie.requireCsrfHeader(http);
        authService.logout(refreshCookie.read(http));
        refreshCookie.clear(response);
        return ResponseEntity.ok(MessageResponse.of("Logged out"));
    }

    @PostMapping("/password-reset")
    @Operation(summary = "Request a password-reset link (sent by email)")
    public ResponseEntity<MessageResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request, HttpServletRequest http) {
        throttle.require("password-reset-ip", limits.getPasswordResetIp(), http.getRemoteAddr());
        // Per-address cap is silent: the answer must stay identical whether or
        // not a mail was sent (no enumeration), it just stops mail-bombing.
        if (throttle.allow("password-reset-email", limits.getPasswordResetEmail(), request.email())) {
            authService.requestPasswordReset(request);
        }
        return ResponseEntity.ok(
                MessageResponse.of("If the email exists, a reset link has been sent."));
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Set a new password using the emailed reset token")
    public MessageResponse confirmPasswordReset(
            @Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        throttle.require("password-reset-confirm-ip", limits.getPasswordResetConfirmIp(),
                http.getRemoteAddr());
        authService.resetPassword(request);
        return MessageResponse.of("Password updated");
    }

    /** Moves the refresh token from the service result into the HttpOnly cookie. */
    private AuthResponse withCookie(AuthResponse auth, HttpServletResponse response) {
        refreshCookie.set(response, auth.refreshToken());
        return auth; // refreshToken is @JsonIgnore'd: it never reaches the body
    }
}
