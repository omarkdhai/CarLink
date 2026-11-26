package com.carlink.qr.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Disguised spellings are folded back to the plain word the model knows. */
class NoteClassifierFoldTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "z.e.b.i            | zebi",
            "z e b i            | zebi",
            "z-e-b-i            | zebi",
            "k@hba              | kahba",
            "3a$ba              | asba",
            "n!komk             | nikomk",
            "9a7ba              | kahba",
            "s0rm               | sorm",
            "mibboun            | miboun",
            "ya 3asba           | ya asba",
            // plain numbers (phone numbers) are left alone
            "appelle 27 808 574 | apele 27 808 574",
    })
    void undoesDisguises(String note, String folded) {
        assertThat(NoteClassifier.fold(NoteClassifier.normalise(note))).isEqualTo(folded);
    }
}
