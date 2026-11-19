package com.carlink.privacy;

import com.carlink.AbstractIntegrationTest;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Security plan S-17: what the daily purge removes, erases and keeps. */
class RetentionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private RetentionService retention;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;

    @Test
    void purgesOldDataAndKeepsWhatIsStillNeeded() {
        Instant now = Instant.now();
        Timestamp old = Timestamp.from(now.minus(Duration.ofDays(400)));
        Timestamp recent = Timestamp.from(now.minus(Duration.ofDays(1)));

        UUID ownerId = UUID.randomUUID();
        userRepository.save(User.builder().id(ownerId).email("retention-" + ownerId + "@example.com")
                .passwordHash("x").role(Role.USER).active(true).build());
        UUID vehicleId = UUID.randomUUID();
        jdbc.update("INSERT INTO vehicles (id, owner_id, license_plate) VALUES (?, ?, '1 TU 1')", vehicleId, ownerId);

        UUID oldConv = conversation(vehicleId, old);
        jdbc.update("INSERT INTO messages (id, conversation_id, content) VALUES (gen_random_uuid(), ?, 'old')", oldConv);
        UUID reportedConv = conversation(vehicleId, old);
        UUID openReport = UUID.randomUUID();
        jdbc.update("INSERT INTO reports (id, reporter_ip, conversation_id, status, created_at) VALUES (?, '10.0.0.1', ?, 'OPEN', ?)",
                openReport, reportedConv, old);
        UUID recentConv = conversation(vehicleId, recent);

        UUID deadToken = UUID.randomUUID();
        jdbc.update("INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                deadToken, ownerId, "h-" + deadToken, old);
        UUID liveToken = UUID.randomUUID();
        jdbc.update("INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                liveToken, ownerId, "h-" + liveToken, Timestamp.from(now.plus(Duration.ofDays(7))));

        UUID oldAudit = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_logs (id, action, ip_address, user_agent, created_at) VALUES (?, 'X', '10.0.0.2', 'UA', ?)",
                oldAudit, old);
        UUID recentAudit = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_logs (id, action, ip_address, user_agent, created_at) VALUES (?, 'X', '10.0.0.3', 'UA', ?)",
                recentAudit, recent);

        UUID oldContact = UUID.randomUUID();
        jdbc.update("INSERT INTO contact_messages (id, name, email, reason, created_at) VALUES (?, 'N', 'n@example.com', 'OTHER', ?)",
                oldContact, old);

        retention.purge(now);

        assertThat(exists("conversations", oldConv)).isFalse();
        assertThat(count("SELECT count(*) FROM messages WHERE conversation_id = ?", oldConv)).isZero();
        assertThat(exists("conversations", reportedConv)).as("kept while its report is OPEN").isTrue();
        assertThat(exists("conversations", recentConv)).isTrue();

        assertThat(exists("refresh_tokens", deadToken)).isFalse();
        assertThat(exists("refresh_tokens", liveToken)).isTrue();

        assertThat(jdbc.queryForObject("SELECT ip_address FROM audit_logs WHERE id = ?", String.class, oldAudit)).isNull();
        assertThat(jdbc.queryForObject("SELECT user_agent FROM audit_logs WHERE id = ?", String.class, oldAudit)).isNull();
        assertThat(jdbc.queryForObject("SELECT ip_address FROM audit_logs WHERE id = ?", String.class, recentAudit))
                .isEqualTo("10.0.0.3");
        assertThat(jdbc.queryForObject("SELECT reporter_ip FROM reports WHERE id = ?", String.class, openReport)).isNull();

        assertThat(exists("contact_messages", oldContact)).isFalse();
    }

    private UUID conversation(UUID vehicleId, Timestamp createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO conversations (id, vehicle_id, channel, status, expires_at, created_at)
                VALUES (?, ?, 'SMS', 'EXPIRED', ?, ?)""", id, vehicleId, createdAt, createdAt);
        return id;
    }

    private boolean exists(String table, UUID id) {
        return count("SELECT count(*) FROM " + table + " WHERE id = ?", id) > 0;
    }

    private long count(String sql, UUID id) {
        Long n = jdbc.queryForObject(sql, Long.class, id);
        return n == null ? 0 : n;
    }
}
