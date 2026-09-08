package com.carlink.auth.service;

import com.carlink.auth.dto.AuthResponse;
import com.carlink.auth.dto.LoginRequest;
import com.carlink.auth.dto.PasswordResetRequest;
import com.carlink.auth.dto.RegisterRequest;
import com.carlink.auth.dto.ResetPasswordRequest;
import com.carlink.auth.dto.VerifyEmailRequest;
import com.carlink.auth.model.EmailVerificationToken;
import com.carlink.auth.model.PasswordResetToken;
import com.carlink.auth.model.RefreshToken;
import com.carlink.auth.repository.EmailVerificationTokenRepository;
import com.carlink.auth.repository.PasswordResetTokenRepository;
import com.carlink.auth.repository.RefreshTokenRepository;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.BadRequestException;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.notification.email.EmailSender;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailSender emailSender;
    @Mock private LoginAttemptService loginAttemptService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private final CarLinkProperties properties = com.carlink.TestProperties.minimal();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(properties);
        authService = new AuthService(
                userRepository, refreshTokenRepository, emailVerificationTokenRepository,
                passwordResetTokenRepository, passwordEncoder, jwtService,
                tokenGenerator, emailSender, properties, loginAttemptService);
    }

    private User savedUser() {
        User user = User.newUser("owner@example.com",
                passwordEncoder.encode("Secret123"), "Ali", "Ben", Role.USER);
        // Optional setup stubs — individual tests may not exercise every save.
        org.mockito.Mockito.lenient()
                .when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(emailVerificationTokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        return user;
    }

    // ---------- Register ----------

    @Test
    void registerCreatesUserAndIssuesTokenPair() {
        savedUser();
        when(userRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(false);

        AuthResponse response = authService.register(
                new RegisterRequest("owner@example.com", "Secret123", "Ali", "Ben", ""));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("owner@example.com");
        assertThat(response.user().role()).isEqualTo("USER");
        verify(emailSender).send(anyString(), anyString(), anyString());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("owner@example.com", "Secret123", "Ali", "Ben", "")))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerNormalizesEmailAndHashesPassword() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        savedUser();
        // Service normalizes the address before the existence check:
        when(userRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(false);

        authService.register(
                new RegisterRequest("Owner@Example.COM", "Secret123", "Ali", "Ben", ""));

        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("owner@example.com");
        assertThat(saved.getPasswordHash()).isNotEqualTo("Secret123");
        assertThat(passwordEncoder.matches("Secret123", saved.getPasswordHash())).isTrue();
    }

    // ---------- Login ----------

    @Test
    void loginSucceedsWithValidCredentials() {
        User user = savedUser();
        when(userRepository.findByEmailIgnoreCase("owner@example.com"))
                .thenReturn(Optional.of(user));

        AuthResponse response = authService.login(
                new LoginRequest("owner@example.com", "Secret123"));

        assertThat(response.accessToken()).isNotBlank();
        verify(loginAttemptService).reset("owner@example.com");
    }

    @Test
    void loginFailsOnWrongPasswordAndRecordsFailure() {
        User user = User.newUser("owner@example.com",
                passwordEncoder.encode("Secret123"), "Ali", "Ben", Role.USER);
        when(userRepository.findByEmailIgnoreCase("owner@example.com"))
                .thenReturn(Optional.of(user));
        when(loginAttemptService.isBlocked("owner@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("owner@example.com", "WrongPass1")))
                .isInstanceOf(UnauthorizedException.class);
        verify(loginAttemptService).recordFailure("owner@example.com");
    }

    @Test
    void loginIsBlockedAfterTooManyFailures() {
        when(loginAttemptService.isBlocked("owner@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("owner@example.com", "Secret123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Too many failed");
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void loginFailsForDisabledAccount() {
        User user = User.newUser("owner@example.com",
                passwordEncoder.encode("Secret123"), "Ali", "Ben", Role.USER);
        user.setActive(false);
        when(userRepository.findByEmailIgnoreCase("owner@example.com"))
                .thenReturn(Optional.of(user));
        when(loginAttemptService.isBlocked("owner@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("owner@example.com", "Secret123")))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---------- Refresh / rotation ----------

    @Test
    void refreshRotatesToken() {
        User user = savedUser();
        RefreshToken existing = RefreshToken.create(user,
                tokenGenerator.sha256("old-refresh-token"),
                java.time.Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(tokenGenerator.sha256("old-refresh-token")))
                .thenReturn(Optional.of(existing));

        AuthResponse response = authService.refresh("old-refresh-token");

        assertThat(response.refreshToken()).isNotEqualTo("old-refresh-token");
        assertThat(existing.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refreshRejectsRevokedTokenAndRevokesFamily() {
        User user = savedUser();
        RefreshToken revoked = RefreshToken.create(user,
                tokenGenerator.sha256("used-token"),
                java.time.Instant.now().plusSeconds(3600));
        revoked.revoke();
        RefreshToken other = RefreshToken.create(user,
                tokenGenerator.sha256("other-token"),
                java.time.Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(tokenGenerator.sha256("used-token")))
                .thenReturn(Optional.of(revoked));
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId()))
                .thenReturn(new ArrayList<>(List.of(other)));

        assertThatThrownBy(() -> authService.refresh("used-token"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(other.isRevoked()).isTrue();
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("nope"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---------- Logout ----------

    @Test
    void logoutRevokesPresentedToken() {
        User user = savedUser();
        RefreshToken token = RefreshToken.create(user,
                tokenGenerator.sha256("session-token"),
                java.time.Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(tokenGenerator.sha256("session-token")))
                .thenReturn(Optional.of(token));

        authService.logout("session-token");

        assertThat(token.isRevoked()).isTrue();
    }

    // ---------- Verify email ----------

    @Test
    void verifyEmailMarksUserVerified() {
        User user = savedUser();
        EmailVerificationToken token = EmailVerificationToken.create(user,
                tokenGenerator.sha256("verify-token"),
                java.time.Instant.now().plusSeconds(300));
        when(emailVerificationTokenRepository.findByTokenHash(
                tokenGenerator.sha256("verify-token")))
                .thenReturn(Optional.of(token));

        authService.verifyEmail(new VerifyEmailRequest("verify-token").token());

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void verifyEmailRejectsUnknownToken() {
        when(emailVerificationTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad"))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    // ---------- Password reset ----------

    @Test
    void resetRequestDoesNotRevealWhetherAccountExists() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com"))
                .thenReturn(Optional.empty());

        authService.requestPasswordReset(
                new PasswordResetRequest("nobody@example.com"));

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void resetRequestEmailsExistingAccount() {
        User user = savedUser();
        when(userRepository.findByEmailIgnoreCase("owner@example.com"))
                .thenReturn(Optional.of(user));

        authService.requestPasswordReset(new PasswordResetRequest("owner@example.com"));

        verify(emailSender).send(anyString(), anyString(), anyString());
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void resetPasswordUpdatesHashAndRevokesSessions() {
        User user = User.newUser("owner@example.com",
                passwordEncoder.encode("OldPass123"), "Ali", "Ben", Role.USER);
        PasswordResetToken token = PasswordResetToken.create(user,
                tokenGenerator.sha256("reset-token"),
                java.time.Instant.now().plusSeconds(300));
        when(passwordResetTokenRepository.findByTokenHash(
                tokenGenerator.sha256("reset-token"))).thenReturn(Optional.of(token));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.resetPassword(new ResetPasswordRequest("reset-token", "NewPass123"));

        assertThat(passwordEncoder.matches("NewPass123", user.getPasswordHash())).isTrue();
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void resetPasswordRejectsUsedToken() {
        PasswordResetToken token = PasswordResetToken.create(
                User.builder().id(UUID.randomUUID()).build(),
                tokenGenerator.sha256("used-reset"),
                java.time.Instant.now().plusSeconds(300));
        token.markUsed();
        when(passwordResetTokenRepository.findByTokenHash(
                tokenGenerator.sha256("used-reset"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordRequest("used-reset", "NewPass123")))
                .isInstanceOf(BadRequestException.class);
    }
}