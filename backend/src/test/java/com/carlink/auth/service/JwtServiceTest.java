package com.carlink.auth.service;

import com.carlink.TestProperties;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
}