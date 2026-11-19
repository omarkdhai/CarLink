package com.carlink.user.model;

import com.carlink.common.entity.BaseEntity;
import com.carlink.privacy.EncryptedPhoneConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A platform user (vehicle owner and/or administrator).
 *
 * <p>{@code phone} is private to the owner — it is never exposed through any
 * API response, HTML, QR code, URL or log. It is used server-side only to
 * route WhatsApp/SMS notifications.</p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Private owner phone. NEVER returned to clients or embedded in QR/web content.
     * Encrypted at rest with AES-256-GCM (security plan S-17); plain in the entity.
     */
    @Convert(converter = EncryptedPhoneConverter.class)
    @Column(length = 255)
    private String phone;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    /** Optional date of birth, collected when the user activates a sticker. */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /**
     * Security plan S-10: copied into every access token as the "tv" claim.
     * Bumped by {@code SessionInvalidator} to kill all outstanding tokens.
     */
    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    public static User newUser(String email, String passwordHash, String firstName,
                               String lastName, Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordHash)
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .active(true)
                .emailVerified(false)
                .build();
    }
}