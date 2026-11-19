package com.carlink.security.session;

import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security plan S-10: the live state of an account (active, role, token
 * version) that every access token is checked against, so a deactivated,
 * demoted or password-reset account loses access at once instead of keeping
 * its JWT until expiry.
 *
 * <p>Owner requests read a copy cached for {@link #TTL}; this instance evicts
 * it right after the transaction that changes the account commits, so only
 * other app instances (if ever scaled out) can lag, by at most the TTL.
 * Admin routes always read the database ({@link #fresh}).
 */
@Component
public class SessionStateCache {

    /** What a token is checked against. */
    /** {@code email} feeds the principal, so the JWT need not carry it (pentest PT-3). */
    public record SessionState(boolean active, Role role, int tokenVersion, String email) {

        static SessionState of(User user) {
            return new SessionState(user.isActive(), user.getRole(), user.getTokenVersion(), user.getEmail());
        }
    }

    static final Duration TTL = Duration.ofSeconds(30);
    private static final int MAX_ENTRIES = 10_000;

    private record Entry(Optional<SessionState> state, long loadedAt) {}

    private final UserRepository userRepository;
    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    public SessionStateCache(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Cached state; empty when the account no longer exists. */
    public Optional<SessionState> get(UUID userId) {
        Entry entry = entries.get(userId);
        if (entry != null && System.nanoTime() - entry.loadedAt() < TTL.toNanos()) {
            return entry.state();
        }
        return fresh(userId);
    }

    /** Reads the database, bypassing (and refreshing) the cache. */
    public Optional<SessionState> fresh(UUID userId) {
        Optional<SessionState> state = userRepository.findById(userId).map(SessionState::of);
        if (entries.size() >= MAX_ENTRIES) {
            long now = System.nanoTime();
            entries.values().removeIf(e -> now - e.loadedAt() >= TTL.toNanos());
            if (entries.size() >= MAX_ENTRIES) {
                entries.clear();
            }
        }
        entries.put(userId, new Entry(state, System.nanoTime()));
        return state;
    }

    public void evict(UUID userId) {
        entries.remove(userId);
    }

    /**
     * Evicts once the surrounding transaction commits (a read in between would
     * otherwise re-cache the old state), or immediately outside a transaction.
     */
    public void evictAfterCommit(UUID userId) {
        evict(userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(userId);
                }
            });
        }
    }
}
