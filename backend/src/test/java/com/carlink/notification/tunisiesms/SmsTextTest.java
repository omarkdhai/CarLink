package com.carlink.notification.tunisiesms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmsTextTest {

    @Test
    void frenchAccentsOfTheGsmAlphabetKeep160() {
        assertThat(SmsText.isGsm("Votre véhicule gêne la sortie, merci !")).isFalse(); // ê is not GSM
        assertThat(SmsText.isGsm("Votre vehicule bloque la sortie, merci ! é è à ù")).isTrue();
        String text = "é".repeat(160);
        assertThat(SmsText.fit(text)).isEqualTo(text);
    }

    @Test
    void arabicSwitchesTo70() {
        String arabic = "سيارتك تسد الطريق، ";
        assertThat(SmsText.isGsm(arabic)).isFalse();
        String fitted = SmsText.fit(arabic.repeat(10));
        assertThat(fitted).endsWith("...");
        assertThat(fitted.length()).isLessThanOrEqualTo(SmsText.UNICODE_LIMIT);
    }

    @Test
    void extensionCharactersCountTwice() {
        String text = "€".repeat(80);                 // 160 septets
        assertThat(SmsText.fit(text)).isEqualTo(text);
        assertThat(SmsText.length(SmsText.fit(text + "€"), true)).isLessThanOrEqualTo(SmsText.GSM_LIMIT);
    }

    @Test
    void neverSplitsAnEmoji() {
        String fitted = SmsText.fit("a".repeat(66) + "😀😀😀");
        assertThat(Character.isHighSurrogate(fitted.charAt(fitted.length() - 4))).isFalse();
        assertThat(fitted.length()).isLessThanOrEqualTo(SmsText.UNICODE_LIMIT);
    }
}
