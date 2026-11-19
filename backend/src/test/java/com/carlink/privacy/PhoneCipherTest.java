package com.carlink.privacy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneCipherTest {

    private static final String KEY = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static final String OTHER_KEY = "ff0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    private final PhoneCipher cipher = new PhoneCipher(KEY);

    @Test
    void roundTripsAndHidesTheNumber() {
        String stored = cipher.encrypt("+21655123456");
        assertThat(stored).startsWith("v1:").doesNotContain("55123456");
        assertThat(cipher.decrypt(stored)).isEqualTo("+21655123456");
    }

    @Test
    void usesAFreshIvEveryTime() {
        assertThat(cipher.encrypt("+21655123456")).isNotEqualTo(cipher.encrypt("+21655123456"));
    }

    @Test
    void passesNullAndLegacyPlaintextThrough() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.decrypt("+21655123456")).isEqualTo("+21655123456");
    }

    @Test
    void refusesAWrongKeyOrATamperedValue() {
        String stored = cipher.encrypt("+21655123456");
        assertThatThrownBy(() -> new PhoneCipher(OTHER_KEY).decrypt(stored))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("55123456");
        String tampered = stored.substring(0, stored.length() - 3) + (stored.endsWith("A") ? "BBB" : "AAA");
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesAMissingOrMalformedKey() {
        assertThatThrownBy(() -> new PhoneCipher("")).hasMessageContaining("PHONE_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new PhoneCipher("zz")).hasMessageContaining("hexadecimal");
        assertThatThrownBy(() -> new PhoneCipher("0011")).hasMessageContaining("32 bytes");
    }
}
