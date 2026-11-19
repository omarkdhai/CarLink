package com.carlink.auth.service;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and validates short-lived JWT <em>access</em> tokens.
 *
 * <p>Refresh tokens are not JWTs — they are opaque random strings stored
 * hashed in the database. Only access tokens are signed JWTs.</p>
 */
@Service
public class JwtService {

    private final CarLinkProperties properties;
    private final SecretKey signingKey;

    public JwtService(CarLinkProperties properties) {
        this.properties = properties;
        this.signingKey = signingKey(properties.jwt().secret());
    }

    /** Values that have been public (committed) at some point: never acceptable. */
    static final Set<String> PUBLIC_SECRETS = Set.of(
            "dev-only-change-me-in-production-please-32chars",
            "replace_with_a_strong_random_secret_at_least_32_chars",
            "change_me", "changeme", "secret");

    /**
     * Security plan S-18: refuse to start with a missing, short, publicly known
     * or obviously low-entropy signing secret — anyone who knows it can mint an
     * ADMIN token. Generate one with {@code openssl rand -hex 48} or
     * {@code node scripts/dev-secrets.mjs}.
     */
    static SecretKey signingKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET (carlink.jwt.secret) is not set.");
        }
        String value = secret.trim();
        if (PUBLIC_SECRETS.contains(value)) {
            throw new IllegalStateException("JWT_SECRET is a publicly known value; generate a random one.");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes (256 bits).");
        }
        if (value.chars().distinct().count() < 10) {
            throw new IllegalStateException("JWT_SECRET has too little variety to be random.");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /** Claim holding {@link User#getTokenVersion()} (security plan S-10). */
    public static final String TOKEN_VERSION_CLAIM = "tv";

    /** Access-token lifetime: shorter for administrators (security plan S-10). */
    public Duration accessTtl(User user) {
        long minutes = user.getRole() == Role.ADMIN
                ? properties.jwt().adminAccessExpirationMinutes()
                : properties.jwt().accessExpirationMinutes();
        return Duration.ofMinutes(minutes);
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTtl(user));
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the access token and returns its claims.
     *
     * @throws JwtException if the token is malformed, expired or signed by an
     *                      unknown key
     */
    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(Claims claims) {
        return claims.getSubject();
    }

    public String extractRole(Claims claims) {
        Object role = claims.get("role");
        return role == null ? Role.USER.name() : role.toString();
    }

    /** The token's version; tokens minted before S-10 carry none and count as 0. */
    public int extractTokenVersion(Claims claims) {
        Object tv = claims.get(TOKEN_VERSION_CLAIM);
        return tv instanceof Number n ? n.intValue() : 0;
    }

    public boolean isAccessTokenExpired(Claims claims) {
        return claims.getExpiration() != null
                && claims.getExpiration().toInstant().isBefore(Instant.now());
    }
}