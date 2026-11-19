package com.carlink.qr.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Security plan S-12: no links reach an owner through the SMS relay. */
class RelayTextSanitizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Pay the fine here https://evil.example/pay now",
            "go to http://1.2.3.4/x",
            "visit www.carlink-fines.com",
            "visit WWW.Example.org/path?x=1",
            "your car was towed: towing-tunis.xyz/pay",
            "see bit.ly/3abc",
            "see example[.]com",
            "see example (.) com",
            "see example dot com",
            "contact parking.gouv.tn",
    })
    void linksAreNeutralised(String input) {
        String out = RelayTextSanitizer.neutraliseLinks(input);
        assertThat(out).contains(RelayTextSanitizer.LINK_MARKER);
        assertThat(out.toLowerCase())
                .doesNotContain("http").doesNotContain("www")
                .doesNotContain(".com").doesNotContain(".xyz").doesNotContain("bit.ly")
                .doesNotContain(".tn");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Your lights are on, I'm in the café opposite.",
            "I moved my car. Me and my kids are waiting at gate 3.",
            "Call me: 22 333 444. Thanks!",
            "Parked 3.5 m from the charger, 80.2 kWh left",
            "Désolé, j'ai touché votre pare-choc. Je suis au 2e étage.",
            "سيارتك سادّة عليّ، عيشك نحّيها",
    })
    void ordinaryNotesAreUntouched(String input) {
        assertThat(RelayTextSanitizer.neutraliseLinks(input)).isEqualTo(input);
    }

    @Test
    void nullStaysNull() {
        assertThat(RelayTextSanitizer.neutraliseLinks(null)).isNull();
    }
}
