package com.carlink.auth.service;

import com.carlink.auth.repository.RefreshTokenRepository;
import com.carlink.security.session.SessionStateCache;
import com.carlink.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Ends every session of an account at once (security plan S-10): bumps its
 * token version, which voids all access tokens already issued, and revokes
 * its refresh tokens. Call it inside the transaction that changes the user
 * (deactivation, role change, password reset).
 */
@Service
@RequiredArgsConstructor
public class SessionInvalidator {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionStateCache sessionStateCache;

    public void invalidateAll(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId()).forEach(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
        sessionStateCache.evictAfterCommit(user.getId());
    }
}
