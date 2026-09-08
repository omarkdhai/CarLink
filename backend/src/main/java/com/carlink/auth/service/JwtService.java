package com.carlink.auth.service;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
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
        this.signingKey = Keys.hmacShaKeyFor(
                properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(
                Duration.ofMinutes(properties.jwt().accessExpirationMinutes()));
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
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

    public boolean isAccessTokenExpired(Claims claims) {
        return claims.getExpiration() != null
                && claims.getExpiration().toInstant().isBefore(Instant.now());
    }
}