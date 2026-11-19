package com.carlink.auth.password;

import com.carlink.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static final String EMAIL = "omar.driver@example.com";

    private final PasswordPolicy policy = new PasswordPolicy(new BreachedPasswordChecker(false, "http://127.0.0.1:9/"));

    @ParameterizedTest
    @ValueSource(strings = {
            "Secret1234",
            "correct horse battery staple",      // passphrase, no digit
            "ma voiture est bleue",              // French passphrase
            "سيارتي زرقاء برشا",                  // Arabic passphrase (multi-byte, < 72 bytes)
            "Tr0ub4dor&3x"})
    void acceptsLongEnoughUnguessablePasswords(String password) {
        assertThatCode(() -> policy.check(password, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void refusesShortPasswords() {
        assertCode("Abc12345!", "PASSWORD_TOO_SHORT");          // 9 characters
    }

    @Test
    void countsCharactersNotBytesForTheMinimum() {
        // 10 Arabic letters are 20 bytes but only 10 characters: accepted.
        assertThatCode(() -> policy.check("صباحالخيرك", EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void refusesMoreThanSeventyTwoBytes() {
        assertCode("ض".repeat(37), "PASSWORD_TOO_LONG");        // 74 bytes, BCrypt would truncate
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password123", "PASSWORD123", "azertyuiop", "1234567890", "motdepasse",
            "aaaaaaaaaaaa", "abababababab", "abcdefghijkl", "9876543210", "qwertyuiopas",
            "Omar.Driver2024", "omardriver!!"})
    void refusesGuessablePasswords(String password) {
        assertCode(password, "PASSWORD_TOO_COMMON");
    }

    @Test
    void refusesBreachedPasswordsWhenTheCheckSaysSo() {
        BreachedPasswordChecker alwaysBreached = new BreachedPasswordChecker(false, "http://127.0.0.1:9/") {
            @Override
            public boolean isBreached(String password) {
                return true;
            }
        };
        assertThatThrownBy(() -> new PasswordPolicy(alwaysBreached).check("Secret1234", EMAIL))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("PASSWORD_BREACHED");
    }

    @Test
    void breachCheckMatchesOnlyRealEntriesAndFailsOpen() {
        // SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
        assertThat(BreachedPasswordChecker.sha1Hex("password"))
                .isEqualTo("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8");
        String body = "003D68EB55068C33ACE09247EE4C639306B:3\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD8:9545824\r\n";
        assertThat(BreachedPasswordChecker.containsSuffix(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8")).isTrue();
        // Add-Padding entries carry a count of 0 and must not match.
        assertThat(BreachedPasswordChecker.containsSuffix(
                "1E4C9B93F3F0682250B6CF8331B7EE68FD8:0", "1E4C9B93F3F0682250B6CF8331B7EE68FD8")).isFalse();
        // Enabled but unreachable: the password is accepted (fail open).
        assertThat(new BreachedPasswordChecker(true, "http://127.0.0.1:9/").isBreached("password")).isFalse();
    }

    private void assertCode(String password, String code) {
        assertThatThrownBy(() -> policy.check(password, EMAIL))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo(code);
    }
}
