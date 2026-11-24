package com.carlink.notification.sms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmsTextTest {

    @Test
    void frenchAccentsMissingFromTheSmsAlphabetAreReplacedSoTheSmsKeeps160() {
        String note = "Garé devant la boîte, ça gêne l’entrée – désolé…";
        String fitted = SmsText.fit(note);
        assertThat(fitted).isEqualTo("Garé devant la boite, ca gene l'entrée - désolé...");
        assertThat(SmsText.isGsm(fitted)).isTrue();
    }

    @Test
    void aUnicodeSmsKeepsEveryCharacterAsTyped() {
        // Arabic already makes it Unicode: replacing ê/ç would gain nothing and alter the note.
        String text = "💡 ضو كرهبتك شاعل\n💬 ça gêne – désolé…";
        assertThat(SmsText.fit(text)).isEqualTo(text);
    }

    @Test
    void gsmAccentsAreKeptAndCountOnce() {
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
