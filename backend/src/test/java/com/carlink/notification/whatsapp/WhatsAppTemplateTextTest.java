package com.carlink.notification.whatsapp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppTemplateTextTest {

    @Test
    void reasonIsTheSameDerjaLineAsTheSms() {
        assertThat(WhatsAppTemplateText.reason("BLOCKING")).isEqualTo("🚧 كرهبتك سادّة عليّ");
    }

    @Test
    void noteIsKeptAsTypedButOnOneLine() {
        assertThat(WhatsAppTemplateText.note("Je suis devant,\r\n\tmerci 🙏")).isEqualTo("Je suis devant, merci 🙏");
        assertThat(WhatsAppTemplateText.note("a      b")).isEqualTo("a   b");
    }

    @Test
    void emptyNoteBecomesADashBecauseWhatsAppRefusesEmptyVariables() {
        assertThat(WhatsAppTemplateText.note(null)).isEqualTo("—");
        assertThat(WhatsAppTemplateText.note(" \n ")).isEqualTo("—");
    }

    @Test
    void veryLongNoteIsCutWithoutBreakingAnEmoji() {
        String note = "a".repeat(WhatsAppTemplateText.NOTE_MAX - 1) + "🙏🙏";
        String text = WhatsAppTemplateText.note(note);
        assertThat(text).endsWith("…");
        assertThat(Character.isHighSurrogate(text.charAt(text.length() - 2))).isFalse();
        assertThat(text.length()).isLessThanOrEqualTo(WhatsAppTemplateText.NOTE_MAX + 1);
    }
}
