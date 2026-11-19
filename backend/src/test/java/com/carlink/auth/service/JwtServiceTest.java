package com.carlink.auth.service;

import com.carlink.TestProperties;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TestProperties.minimal());
    }

    private User user() {
        return User.newUser("owner@example.com", "hash", "Ali", "Ben", Role.USER);
    }

    @Test
    void issuesAndParsesAccessToken() {
        User user = user();
        String token = jwtService.issueAccessToken(user);

        Claims claims = jwtService.parseAccessToken(token);
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email")).isEqualTo("owner@example.com");
        assertThat(claims.get("role")).isEqualTo("USER");
        assertThat(claims.getExpiration()).isAfter(new java.util.Date());
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.issueAccessToken(user());
        // Flip one character in the signature segment
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + flipLastChar(parts[2]);

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    private String flipLastChar(String s) {
        char c = s.charAt(s.length() - 1);
        c = c == 'A' ? 'B' : 'A';
        return s.substring(0, s.length() - 1) + c;
    }

    @Test
    void extractsUserIdAndRole() {
        User user = user();
        String token = jwtService.issueAccessToken(user);
        Claims claims = jwtService.parseAccessToken(token);
        assertThat(jwtService.extractUserId(claims))
                .isEqualTo(user.getId().toString());
        assertThat(jwtService.extractRole(claims)).isEqualTo("USER");
    }

    // ---------- S-18: signing-secret guard ----------

    @Test
    void refusesMissingSecret() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> JwtService.signingKey("  "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not set");
    }

    @Test
    void refusesPublicSecret() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> JwtService.signingKey("dev-only-change-me-in-production-please-32chars"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("publicly known");
    }

    @Test
    void refusesShortSecret() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> JwtService.signingKey("abcdef0123456789"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    @Test
    void refusesLowEntropySecret() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> JwtService.signingKey("a".repeat(64)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("variety");
    }

    @Test
    void acceptsRandomHexSecret() {
        org.assertj.core.api.Assertions.assertThat(
                JwtService.signingKey("9f2c4e7a1b3d5f60718293a4b5c6d7e8f9012a3b4c5d6e7f8091a2b3c4d5e6f7")).isNotNull();
    }
}
