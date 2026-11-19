package com.carlink.privacy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scheduled data minimisation (security plan S-17). Once a day it:
 *
 * <ul>
 *   <li>deletes conversations, and by cascade their messages, older than
 *       {@code conversation-days} — except those under an OPEN abuse report,
 *       which stay until a moderator closes the report;</li>
 *   <li>deletes refresh, password-reset and e-mail-verification tokens once
 *       they have been expired, revoked or used for {@code token-grace-days};</li>
 *   <li>erases IP addresses and user agents from audit logs and reports older
 *       than {@code ip-days} (the rows themselves stay, for moderation history);</li>
 *   <li>deletes public "Contact us" messages older than {@code contact-message-days}.</li>
 * </ul>
 *
 * Plain SQL, so no entity is loaded; only counts are logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final JdbcTemplate jdbc;
    private final RetentionProperties props;

    @Scheduled(cron = "${carlink.retention.cron:0 30 3 * * *}")
    public void scheduledPurge() {
        if (!props.isEnabled()) {
            return;
        }
        Map<String, Integer> counts = purge(Instant.now());
        log.info("Retention purge done: {}", counts);
    }

    /** Runs every rule against {@code now}; returns the affected row count per rule. */
    @Transactional
    public Map<String, Integer> purge(Instant now) {
        Timestamp conversationCutoff = before(now, props.getConversationDays());
        Timestamp tokenCutoff = before(now, props.getTokenGraceDays());
        Timestamp ipCutoff = before(now, props.getIpDays());
        Timestamp contactCutoff = before(now, props.getContactMessageDays());

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("conversations", jdbc.update("""
                DELETE FROM conversations c
                 WHERE c.created_at < ?
                   AND NOT EXISTS (SELECT 1 FROM reports r
                                    WHERE r.conversation_id = c.id AND r.status = 'OPEN')""",
                conversationCutoff));
        counts.put("refreshTokens", jdbc.update(
                "DELETE FROM refresh_tokens WHERE expires_at < ? OR revoked_at < ?",
                tokenCutoff, tokenCutoff));
        counts.put("passwordResetTokens", jdbc.update(
                "DELETE FROM password_reset_tokens WHERE expires_at < ? OR used_at < ?",
                tokenCutoff, tokenCutoff));
        counts.put("emailVerificationTokens", jdbc.update(
                "DELETE FROM email_verification_tokens WHERE expires_at < ? OR used_at < ?",
                tokenCutoff, tokenCutoff));
        counts.put("auditLogIps", jdbc.update("""
                UPDATE audit_logs SET ip_address = NULL, user_agent = NULL
                 WHERE created_at < ? AND (ip_address IS NOT NULL OR user_agent IS NOT NULL)""",
                ipCutoff));
        counts.put("reportIps", jdbc.update(
                "UPDATE reports SET reporter_ip = NULL WHERE created_at < ? AND reporter_ip IS NOT NULL",
                ipCutoff));
        counts.put("contactMessages", jdbc.update(
                "DELETE FROM contact_messages WHERE created_at < ?", contactCutoff));
        return counts;
    }

    private static Timestamp before(Instant now, int days) {
        return Timestamp.from(now.minus(Duration.ofDays(days)));
    }
}
