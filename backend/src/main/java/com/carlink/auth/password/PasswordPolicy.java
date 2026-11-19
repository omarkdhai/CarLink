package com.carlink.auth.password;

import com.carlink.common.exception.BadRequestException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Policy for every password a user chooses (security plan S-14), following
 * NIST SP 800-63B: length over composition rules.
 *
 * <ul>
 *   <li>At least {@value #MIN_LENGTH} characters and at most {@value #MAX_BYTES}
 *       bytes (BCrypt ignores the rest, and Spring Security rejects longer
 *       input). Any characters, spaces included: passphrases are welcome, and
 *       there is no "letter + digit" rule.</li>
 *   <li>Not a common password, one or two repeated characters, a keyboard or
 *       digit run, or something built on the user's e-mail name.</li>
 *   <li>Optionally, not in a public breach corpus ({@link BreachedPasswordChecker}).</li>
 * </ul>
 *
 * Every refusal is a 400 with a stable code the SPA translates. The password
 * itself is never logged.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private static final String[] RUNS = {
            "01234567890123", "abcdefghijklmnopqrstuvwxyz",
            "qwertyuiopasdfghjklzxcvbnm", "azertyuiopqsdfghjklmwxcvbn"};

    private final Set<String> common;
    private final BreachedPasswordChecker breachChecker;

    public PasswordPolicy(BreachedPasswordChecker breachChecker) {
        this.breachChecker = breachChecker;
        this.common = loadCommon();
    }

    /** Throws a 400 when {@code password} may not be used by the owner of {@code email}. */
    public void check(String password, String email) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new BadRequestException("PASSWORD_TOO_SHORT",
                    "Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BadRequestException("PASSWORD_TOO_LONG", "Password is too long");
        }
        if (isGuessable(password, email)) {
            throw new BadRequestException("PASSWORD_TOO_COMMON", "This password is too easy to guess");
        }
        if (breachChecker.isBreached(password)) {
            throw new BadRequestException("PASSWORD_BREACHED",
                    "This password appears in a known data breach");
        }
    }

    boolean isGuessable(String password, String email) {
        String lower = password.toLowerCase(Locale.ROOT);
        String compact = lower.replaceAll("\\s+", "");
        if (common.contains(lower) || common.contains(compact)) {
            return true;
        }
        if (compact.chars().distinct().count() <= 2) {
            return true; // aaaaaaaaaa, ababababab
        }
        for (String run : RUNS) {
            String reversed = new StringBuilder(run).reverse().toString();
            if (run.contains(compact) || reversed.contains(compact)) {
                return true;
            }
        }
        if (email != null) {
            String local = alnum(email.toLowerCase(Locale.ROOT).split("@", 2)[0]);
            if (local.length() >= 4 && alnum(compact).contains(local)) {
                return true;
            }
        }
        return false;
    }

    private static String alnum(String value) {
        return value.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static Set<String> loadCommon() {
        try (var in = new ClassPathResource("security/common-passwords.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("security/common-passwords.txt is missing", e);
        }
    }
}
