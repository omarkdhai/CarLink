package com.carlink.qr.service;

import com.carlink.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteSpamFilterTest {

    private final NoteSpamFilter filter = new NoteSpamFilter(true);

    @ParameterizedTest
    @ValueSource(strings = {
            "كرهبتك سادّة عليّ، نستناك قدام الباب",
            "karhbtek sakra 3lia, ena nestanna 9oddem",
            "Vos phares sont allumés, appelez-moi au 27 808 574",
            "Your car is blocking my garage, please move it",
            "سيارتك تسد الطريق من فضلك",
            "merci",
            "Je suis au café d'en face",
            "27808574",
            "contact: ali.ben@example.tn",
    })
    void notesAboutTheCarOrNeutralPass(String note) {
        assertThat(filter.wouldRefuse(note)).as(note).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Gagnez un iPhone 15 gratuitement",
            "hot girls in your area, add me",
            "follow me on instagram",
            "asdfghjkl",
            "nique ta mère",
    })
    void clearSpamIsRefused(String note) {
        assertThat(filter.wouldRefuse(note)).as(note).isTrue();
    }

    @Test
    void emptyNoteAlwaysPasses() {
        assertThatCode(() -> filter.requireOnTopic(null)).doesNotThrowAnyException();
        assertThatCode(() -> filter.requireOnTopic("   ")).doesNotThrowAnyException();
    }

    @Test
    void refusalHasTheCodeAndNeverRepeatsTheNote() {
        assertThatThrownBy(() -> filter.requireOnTopic("Gagnez un iPhone 15 gratuitement"))
                .isInstanceOf(ApiException.class)
                .hasMessageNotContaining("iPhone")
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(NoteSpamFilter.ERROR_CODE));
    }

    @Test
    void canBeTurnedOff() {
        assertThatCode(() -> new NoteSpamFilter(false).requireOnTopic("Gagnez un iPhone 15 gratuitement"))
                .doesNotThrowAnyException();
    }

    @Test
    void thresholdMustBeAProbability() {
        assertThatThrownBy(() -> new NoteSpamFilter(true, 1.5)).isInstanceOf(IllegalStateException.class);
    }
}
