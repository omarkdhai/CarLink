package com.carlink.notification.sms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertSmsTest {

    @Test
    void reasonInDerjaThenTheNoteExactlyAsTyped() {
        assertThat(AlertSms.compose("LIGHTS", "Je suis au café d'à côté"))
                .isEqualTo("💡 ضو كرهبتك شاعل\n💬 Je suis au café d'à côté");   // accents kept as typed
        assertThat(AlertSms.compose("BLOCKING", " نستنى فيك قدام الباب "))
                .isEqualTo("🚧 كرهبتك سادّة عليّ\n💬 نستنى فيك قدام الباب");
        assertThat(AlertSms.compose("HIT_CAR", "Sorry! I'm at the café 😅"))
                .isEqualTo("💥 ضربت كرهبتك بلاش ما نقصد\n💬 Sorry! I'm at the café 😅");
    }

    @Test
    void withoutNoteTheSmsIsTheReasonLineOnly() {
        assertThat(AlertSms.compose("EV_CHARGE", "   ")).isEqualTo("🔋 كرهبتك شحنت، نحب نشحن كرهبتي");
        assertThat(AlertSms.compose("WINDOWS_OPEN", null)).isEqualTo("☔ شبابك كرهبتك محلولين");
    }

    @Test
    void unknownReasonStillProducesAnAlert() {
        assertThat(AlertSms.compose("NOPE", null)).isEqualTo("❗ فما شكون يحب يكلمك");
        assertThat(AlertSms.compose(null, null)).isEqualTo("❗ فما شكون يحب يكلمك");
    }

    @Test
    void aNoteOfExactlyTheAdvertisedLengthFitsUncutAndOneMoreIsShortened() {
        AlertSms.noteLimits().forEach((reason, room) -> {
            String note = "a".repeat(room);
            String sms = AlertSms.compose(reason, note);
            assertThat(sms).endsWith(note);
            assertThat(sms.length()).isLessThanOrEqualTo(SmsText.UNICODE_LIMIT);

            String longer = AlertSms.compose(reason, note + "b");
            assertThat(longer).endsWith("...").startsWith(AlertSms.line(reason));
            assertThat(longer.length()).isLessThanOrEqualTo(SmsText.UNICODE_LIMIT);
        });
    }

    @Test
    void everyReasonLeavesRoomForANote() {
        assertThat(AlertSms.noteLimits())
                .containsOnlyKeys("BLOCKING", "LIGHTS", "LEFT_OPEN", "HIT_CAR", "WINDOWS_OPEN", "EV_CHARGE")
                .containsEntry("LIGHTS", 45)
                .allSatisfy((reason, room) -> assertThat(room).as(reason).isGreaterThanOrEqualTo(30));
    }
}
