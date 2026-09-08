package com.carlink.auth.model;

import com.carlink.user.model.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Single-use token for resetting a forgotten password.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends AbstractOneTimeToken {

    public static PasswordResetToken create(User user, String tokenHash, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.init(user, tokenHash, expiresAt);
        return token;
    }
}