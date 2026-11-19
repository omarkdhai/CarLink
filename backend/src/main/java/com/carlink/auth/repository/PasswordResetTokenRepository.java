package com.carlink.auth.repository;

import com.carlink.auth.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Security plan S-21: marks every still-unused reset token of the user as
     * used, so only the newest e-mailed link works (and none after a reset).
     */
    @Modifying(flushAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now "
            + "where t.user.id = :userId and t.usedAt is null")
    int invalidateUnused(@Param("userId") UUID userId, @Param("now") Instant now);
}
