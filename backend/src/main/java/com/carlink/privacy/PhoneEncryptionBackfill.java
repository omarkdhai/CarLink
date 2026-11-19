package com.carlink.privacy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-time, idempotent migration (security plan S-17): encrypts every
 * {@code users.phone} still stored in plaintext. Runs at startup because the
 * key lives in the environment, which a Flyway migration cannot see. Rows
 * already carrying the {@code v1:} prefix are left alone, so later starts do
 * nothing. Only the count is logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneEncryptionBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final PhoneCipher cipher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, phone FROM users WHERE phone IS NOT NULL AND phone NOT LIKE 'v1:%'");
        for (Map<String, Object> row : rows) {
            jdbc.update("UPDATE users SET phone = ? WHERE id = ?",
                    cipher.encrypt((String) row.get("phone")), (UUID) row.get("id"));
        }
        if (!rows.isEmpty()) {
            log.info("Encrypted {} plaintext phone number(s) at rest", rows.size());
        }
    }
}
