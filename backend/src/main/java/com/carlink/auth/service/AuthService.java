package com.carlink.auth.service;

import com.carlink.auth.dto.AuthResponse;
import com.carlink.auth.dto.LoginRequest;
import com.carlink.auth.dto.PasswordResetRequest;
import com.carlink.auth.dto.RegisterRequest;
import com.carlink.auth.dto.ResetPasswordRequest;
import com.carlink.auth.model.EmailVerificationToken;
import com.carlink.auth.model.PasswordResetToken;
import com.carlink.auth.model.RefreshToken;
import com.carlink.auth.repository.EmailVerificationTokenRepository;
import com.carlink.auth.repository.PasswordResetTokenRepository;
import com.carlink.auth.repository.RefreshTokenRepository;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.BadRequestException;
import com.carlink.common.exception.NotFoundException;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.notification.email.EmailSender;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.model.UserResponse;
import com.carlink.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Registration, login, token refresh/rotation, logout, email verification and
 * password reset.
 *
 * <p>Security rules enforced here:</p>
 * <ul>
 *   <li>Refresh tokens are opaque, single-use, stored as SHA-256 hashes and
 *       rotated on every use.</li>
 *   <li>The response to registration and to password-reset requests never
 *       reveals whether an account exists (anti-enumeration).</li>
 *   <li>No error path returns passwords, tokens, or the private phone number.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PASSWORD_PATTERN_MESSAGE =
            "Password must be 8-72 characters and contain letters and digits";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenGenerator tokenGenerator;
    private final EmailSender emailSender;
    private final CarLinkProperties properties;
    private final LoginAttemptService loginAttemptService;

    // ---------- Register ----------

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            // Same response as success, minus the token pair — prevents
            // enumeration while staying honest about the failed outcome.
            throw new BadRequestException("A verification link was sent. Check your email.");
        }

        User user = User.newUser(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim(),
                Role.USER);
        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone());
        }
        userRepository.save(user);

        sendVerificationEmail(user);

        // New accounts start verified=false; issue tokens immediately so the
        // owner can use the app and verify later from a zero-click link.
        return issueTokenPair(user);
    }

    private void sendVerificationEmail(User user) {
        String raw = rawToken();
        EmailVerificationToken token = EmailVerificationToken.create(
                user,
                tokenGenerator.sha256(raw),
                nowPlus(Duration.ofMinutes(properties.email().tokenExpiryMinutes())));
        emailVerificationTokenRepository.save(token);
        // DELIBERATE dev-only logging of the link via LogEmailSender (see its javadoc).
        emailSender.send(user.getEmail(),
                "Vérification de votre adresse email",
                "Bonjour " + user.getFirstName() + ",\n\n"
                        + "Veuillez confirmer votre adresse email en cliquant sur ce lien :\n"
                        + verificationUrl(raw));
    }

    // ---------- Verify email ----------

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = emailVerificationTokenRepository
                .findByTokenHash(tokenGenerator.sha256(rawToken))
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification link"));

        if (token.isUsed() || token.isExpired()) {
            throw new BadRequestException("Invalid or expired verification link");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        token.markUsed();
        emailVerificationTokenRepository.save(token);
    }

    // ---------- Login ----------

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        if (loginAttemptService.isBlocked(email)) {
            throw new UnauthorizedException("Too many failed attempts. Try again later.");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .filter(User::isActive)
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(email);
                    log.warn("Login failure for email hash {}",
                            tokenGenerator.sha256(email));
                    return new UnauthorizedException("Invalid email or password");
                });

        loginAttemptService.reset(email);
        return issueTokenPair(user);
    }

    // ---------- Refresh / logout ----------

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(tokenGenerator.sha256(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired session"));

        if (token.isRevoked() || token.isExpired()) {
            revokeFamily(token.getUser().getId());
            throw new UnauthorizedException("Invalid or expired session");
        }

        // Rotate: the old refresh token is single-use.
        token.revoke();
        refreshTokenRepository.save(token);

        return issueTokenPair(token.getUser());
    }

    /** Revokes all active refresh tokens for a user (logout everywhere). */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(tokenGenerator.sha256(rawRefreshToken))
                .ifPresent(it -> {
                    it.revoke();
                    refreshTokenRepository.save(it);
                });
    }

    /** Called when a token is invalid/expired: revoke the whole session family. */
    private void revokeFamily(java.util.UUID userId) {
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId)
                .forEach(it -> {
                    it.revoke();
                    refreshTokenRepository.save(it);
                });
    }

    // ---------- Password reset ----------

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        String email = request.email().trim().toLowerCase();
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            String raw = rawToken();
            PasswordResetToken token = PasswordResetToken.create(
                    user,
                    tokenGenerator.sha256(raw),
                    nowPlus(Duration.ofMinutes(properties.email().tokenExpiryMinutes())));
            passwordResetTokenRepository.save(token);
            emailSender.send(user.getEmail(),
                    "Réinitialisation de votre mot de passe",
                    "Bonjour " + user.getFirstName() + ",\n\n"
                            + "Cliquez sur ce lien pour réinitialiser votre mot de passe :\n"
                            + resetUrl(raw)
                            + "\n\nCe lien expire dans "
                            + properties.email().tokenExpiryMinutes() + " minutes.");
        });
        // Identical answer whether or not the account exists.
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(tokenGenerator.sha256(request.token()))
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link"));

        if (token.isUsed() || token.isExpired()) {
            throw new BadRequestException("Invalid or expired reset link");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setEmailVerified(true);
        userRepository.save(user);
        token.markUsed();
        passwordResetTokenRepository.save(token);

        // Invalidate all existing sessions after a password change.
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId()).forEach(it -> {
            it.revoke();
            refreshTokenRepository.save(it);
        });
    }

    // ---------- Helpers ----------

    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtService.issueAccessToken(user);
        String rawRefresh = rawToken();
        RefreshToken refresh = RefreshToken.create(
                user,
                tokenGenerator.sha256(rawRefresh),
                nowPlus(Duration.ofDays(properties.jwt().refreshExpirationDays())));
        refreshTokenRepository.save(refresh);

        return new AuthResponse(
                accessToken,
                rawRefresh,
                properties.jwt().accessExpirationMinutes() * 60L,
                UserResponse.from(user));
    }

    private String rawToken() {
        return tokenGenerator.generateUrlSafe(32);
    }

    private Instant nowPlus(Duration duration) {
        return Instant.now().plus(duration);
    }

    private String verificationUrl(String rawToken) {
        return properties.publicUrl() + "/verify-email?token=" + rawToken;
    }

    private String resetUrl(String rawToken) {
        return properties.publicUrl() + "/reset-password?token=" + rawToken;
    }
}