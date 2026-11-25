package com.carlink.qr.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quality gate for the note classifier, measured on notes it was not trained on.
 * Fails the build if a change to the examples (or the model) makes it block
 * honest alerts or miss too much spam. Prints the numbers and every mistake.
 */
class NoteClassifierEvaluationTest {

    /** Same as the production default (carlink.contact.spam-filter.threshold). */
    private static final double THRESHOLD = 0.8;

    @Test
    void heldOutNotes() {
        NoteClassifier classifier = NoteClassifier.fromBundledExamples();
        List<NoteClassifier.Example> test = NoteClassifier.load("spam/notes-test.tsv");

        int falseBlocks = 0, allowed = 0, caught = 0, blocked = 0;
        for (NoteClassifier.Example e : test) {
            double p = classifier.blockProbability(e.note());
            boolean refused = p >= THRESHOLD;
            if (e.blocked()) {
                blocked++;
                if (refused) caught++;
            } else {
                allowed++;
                if (refused) falseBlocks++;
            }
            if (refused != e.blocked()) {
                System.out.printf("miss %.2f | expected %s | %s%n", p, e.blocked() ? "BLOCK" : "ALLOW", e.note());
            }
        }
        System.out.printf("held-out @%.1f: honest notes wrongly blocked %d/%d, spam caught %d/%d%n",
                THRESHOLD, falseBlocks, allowed, caught, blocked);

        assertThat(falseBlocks).as("honest held-out notes wrongly blocked").isLessThanOrEqualTo(1);
        assertThat(caught).as("held-out spam caught").isGreaterThanOrEqualTo(blocked * 50 / 100);
    }

    @Test
    void crossValidation() {
        List<NoteClassifier.Example> all = new ArrayList<>(NoteClassifier.load(NoteClassifier.TRAINING_FILE));
        Collections.shuffle(all, new Random(7));
        int folds = 5;
        int falseBlocks = 0, allowed = 0, caught = 0, blocked = 0;
        for (int k = 0; k < folds; k++) {
            List<NoteClassifier.Example> train = new ArrayList<>();
            List<NoteClassifier.Example> held = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                (i % folds == k ? held : train).add(all.get(i));
            }
            NoteClassifier c = NoteClassifier.train(train);
            for (NoteClassifier.Example e : held) {
                boolean refused = c.blockProbability(e.note()) >= THRESHOLD;
                if (e.blocked()) {
                    blocked++;
                    if (refused) caught++;
                } else {
                    allowed++;
                    if (refused) falseBlocks++;
                }
            }
        }
        System.out.printf("5-fold CV @%.1f: honest notes wrongly blocked %d/%d, spam caught %d/%d%n",
                THRESHOLD, falseBlocks, allowed, caught, blocked);

        assertThat(falseBlocks * 100.0 / allowed).as("% honest notes wrongly blocked").isLessThanOrEqualTo(2.0);
        assertThat(caught * 100.0 / blocked).as("% spam caught").isGreaterThanOrEqualTo(30.0);
    }

    @Test
    void trainingIsDeterministic() {
        NoteClassifier a = NoteClassifier.fromBundledExamples();
        NoteClassifier b = NoteClassifier.fromBundledExamples();
        for (String note : List.of("karhbtek sakra 3lia", "gagnez un iphone", "merci")) {
            assertThat(a.blockProbability(note)).isEqualTo(b.blockProbability(note));
        }
    }
}
