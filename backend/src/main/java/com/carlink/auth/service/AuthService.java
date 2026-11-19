package com.carlink.auth.service;

import com.carlink.auth.dto.AuthResponse;
import com.carlink.auth.dto.LoginRequest;
import com.carlink.auth.dto.PasswordResetRequest;
import com.carlink.auth.email.PasswordResetEmailComposer;
import com.carlink.auth.dto.RegisterRequest;
import com.carlink.auth.dto.ResetPasswordRequest;
import com.carlink.auth.model.EmailVerificationToken;
import com.carlink.auth.password.PasswordPolicy;
import com.carlink.auth.model.PasswordResetToken;
import com.carlink.auth.model.RefreshToken;
import com.carlink.auth.repository.EmailVerificationTokenRepository;
import com.carlink.auth.repository.PasswordResetTokenRepository;
import com.carlink.auth.repository.RefreshTokenRepository;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.BadRequestException;
import com.carlink.common.exception.ConflictException;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.notification.email.EmailSender;
import com.carlink.sticker.model.Sticker;
import com.carlink.sticker.model.StickerStatus;
import com.carlink.sticker.repository.StickerRepository;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.model.UserResponse;
import com.carlink.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenGenerator tokenGenerator;
    private final EmailSender emailSender;
    private final PasswordResetEmailComposer passwordResetEmailComposer;
    private final CarLinkProperties properties;
    private final LoginAttemptService loginAttemptService;
    private final StickerRepository stickerRepository;
    private final SessionInvalidator sessionInvalidator;
    private final PasswordPolicy passwordPolicy;

    // ---------- Register ----------

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        // An account exists only to own a sticker: reject registration unless
        // the supplied code maps to a claimable (UNBOUND / DEACTIVATED) sticker.
        // Checked BEFORE the e-mail (security plan S-13): the "already
        // registered" answer is then only reachable with a code from a real,
        // unclaimed sticker in hand, so the form is no e-mail oracle. Both
        // checks also sit behind the per-IP register throttle (S-03).
        String codeHash = tokenGenerator.sha256(
                tokenGenerator.normalizeActivationCode(request.stickerCode()));
        Sticker sticker = stickerRepository.findByActivationCodeHash(codeHash)
                .orElseThrow(() -> new BadRequestException("INVALID_STICKER_CODE",
                        "That sticker code could not be found. Check the code printed "
                                + "under the QR and try again."));
        if (sticker.getStatus() == StickerStatus.BOUND) {
            // Same answer as the activation path; never says who owns it.
            throw new ConflictException("STICKER_ALREADY_BOUND",
                    "This sticker is already linked to an account.");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            // A distinct code lets the frontend render a "Sign in" call-to-action.
            throw new BadRequestException("EMAIL_ALREADY_REGISTERED",
                    "This email is already registered. Please sign in instead.");
        }
        passwordPolicy.check(request.password(), email);

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

        // The sticker is bound later, from the car-details form
        // (activateByCode). Registration only validates that the supplied code
        // maps to a claimable sticker.

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
                .orElseThrow(() -> new BadRequestException("INVALID_VERIFICATION_LINK", "Invalid or expired verification link"));

        if (token.isUsed() || token.isExpired()) {
            throw new BadRequestException("INVALID_VERIFICATION_LINK", "Invalid or expired verification link");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        token.markUsed();
        emailVerificationTokenRepository.save(token);
    }

    // ---------- Login ----------

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = request.email().trim().toLowerCase();

        if (loginAttemptService.isBlocked(clientIp, email)) {
            throw new UnauthorizedException("TOO_MANY_LOGIN_ATTEMPTS", "Too many failed attempts. Try again later.");
        }

        java.util.Optional<User> candidate = userRepository.findByEmailIgnoreCase(email);
        // S-04: always pay for one BCrypt comparison. Skipping it for unknown
        // e-mails made those answers measurably faster, revealing which
        // addresses have an account.
        // No stored password exceeds 72 bytes (S-14) and BCrypt refuses longer
        // input, so such a password cannot match. Still pay one comparison.
        boolean tooLong = request.password().getBytes(StandardCharsets.UTF_8).length
                > PasswordPolicy.MAX_BYTES;
        boolean passwordOk = passwordEncoder.matches(tooLong ? "-" : request.password(),
                candidate.map(User::getPasswordHash).orElseGet(this::timingDummyHash)) && !tooLong;
        User user = candidate
                .filter(u -> passwordOk)
                .filter(User::isActive)
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(clientIp, email);
                    log.warn("Login failure for email hash {}",
                            tokenGenerator.sha256(email));
                    return new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password");
                });

        loginAttemptService.reset(clientIp, email);
        return issueTokenPair(user);
    }

    // ---------- Refresh / logout ----------

    // noRollbackFor: presenting a used token revokes the whole session family
    // and then answers 401. Without this the 401 rolled the revocation back, so
    // reuse detection never took effect.
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(tokenGenerator.sha256(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired session"));

        if (token.isRevoked() || token.isExpired()) {
            revokeFamily(token.getUser().getId());
            throw new UnauthorizedException("Invalid or expired session");
        }

        // A deactivated account must not keep refreshing a prior session.
        User user = token.getUser();
        if (!user.isActive()) {
            throw new UnauthorizedException("Invalid or expired session");
        }

        // Rotate: the old refresh token is single-use.
        token.revoke();
        refreshTokenRepository.save(token);

        return issueTokenPair(user);
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
            var content = passwordResetEmailComposer.compose(user.getFirstName(), resetUrl(raw), (int) properties.email().tokenExpiryMinutes());
            emailSender.sendHtml(user.getEmail(), content.subject(), content.html(), content.text());
        });
        // Identical answer whether or not the account exists.
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(tokenGenerator.sha256(request.token()))
                .orElseThrow(() -> new BadRequestException("INVALID_RESET_LINK", "Invalid or expired reset link"));

        if (token.isUsed() || token.isExpired()) {
            throw new BadRequestException("INVALID_RESET_LINK", "Invalid or expired reset link");
        }

        User user = token.getUser();
        passwordPolicy.check(request.newPassword(), user.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setEmailVerified(true);
        // End every existing session: refresh tokens AND live access tokens (S-10).
        sessionInvalidator.invalidateAll(user);
        userRepository.save(user);
        token.markUsed();
        passwordResetTokenRepository.save(token);
    }

    // ---------- Helpers ----------

    /** BCrypt hash of a random throwaway value, compared against for unknown e-mails. */
    private volatile String timingDummyHash;

    private String timingDummyHash() {
        String hash = timingDummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode(tokenGenerator.generateUrlSafe(24));
            timingDummyHash = hash;
        }
        return hash;
    }

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
                jwtService.accessTtl(user).toSeconds(),
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