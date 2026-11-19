package com.carlink.user.service;

import com.carlink.admin.service.AuditLogService;
import com.carlink.common.exception.BadRequestException;
import com.carlink.common.exception.NotFoundException;
import com.carlink.security.monitoring.SecurityEvents;
import com.carlink.security.session.SessionStateCache;
import com.carlink.sticker.repository.StickerRepository;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Self-service account deletion (security plan S-17; right to erasure under
 * Tunisian Organic Law 2004-63 and the GDPR).
 *
 * <p>The owner confirms with their password. Then, in one transaction: every
 * sticker they held is released (claimable again, no owner), the user row is
 * hard-deleted — the database removes vehicles, QR codes, conversations,
 * messages and tokens with it — and live access tokens stop working at once
 * (S-10 session state). Only an audit entry with the account id remains.
 *
 * <p>Guest orders are not linked to accounts and are kept as business
 * records (docs/security.md). Administrators cannot delete themselves here,
 * so the last admin can never disappear by accident.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final StickerRepository stickerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final SessionStateCache sessionStateCache;
    private final SecurityEvents securityEvents;

    @Transactional
    public void deleteOwnAccount(UUID userId, String password, String ipAddress, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("ADMIN_ACCOUNT_DELETION",
                    "Administrator accounts are removed by another administrator.");
        }
        // 400, not 401: a 401 would make the SPA try a token refresh and retry.
        boolean tooLong = password == null || password.getBytes(StandardCharsets.UTF_8).length > 72;
        if (tooLong || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("INVALID_PASSWORD", "The password is not correct.");
        }

        stickerRepository.releaseAllOwnedBy(userId, Instant.now());
        auditLogService.record("ACCOUNT_DELETED", "USER", userId, null, ipAddress, userAgent, Map.of());
        userRepository.hardDelete(userId);
        sessionStateCache.evictAfterCommit(userId);
        securityEvents.record(SecurityEvents.Type.ACCOUNT_DELETED);
    }
}
