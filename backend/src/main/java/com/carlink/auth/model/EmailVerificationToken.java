package com.carlink.auth.model;

import com.carlink.user.model.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Single-use token that confirms ownership of the registered email address.
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken extends AbstractOneTimeToken {

    public static EmailVerificationToken create(User user, String tokenHash, Instant expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.init(user, tokenHash, expiresAt);
        return token;
    }
}