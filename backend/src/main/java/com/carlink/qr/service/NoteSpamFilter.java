package com.carlink.qr.service;

import com.carlink.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Refuses alert notes that are not about the car (security plan S-12): every
 * alert becomes an SMS and a WhatsApp message from a trusted sender to a phone
 * the visitor cannot see, so ads, scams, dating spam, insults, threats,
 * unrelated chat and gibberish must not get through.
 *
 * <p>The decision comes from {@link NoteClassifier}, a small model built into
 * the backend and trained on labelled example notes in Tunisian Derja (Arabic
 * and Latin letters), Arabic, French and English. The note never leaves the
 * server. A note is refused only when the model is confident
 * ({@code carlink.contact.spam-filter.threshold}, default 0.8), because blocking
 * a real alert is worse than letting some spam through. An empty note always
 * passes: the reason the visitor picked is about the car. Phone numbers and
 * e-mail addresses are welcome.</p>
 *
 * <p>The refusal never repeats the note, and the log line has only the score.</p>
 */
@Slf4j
@Component
public class NoteSpamFilter {

    public static final String ERROR_CODE = "NOTE_OFF_TOPIC";

    private final boolean enabled;
    private final double threshold;

    @Autowired
    public NoteSpamFilter(@Value("${carlink.contact.spam-filter.enabled:true}") boolean enabled,
                          @Value("${carlink.contact.spam-filter.threshold:0.8}") double threshold) {
        if (threshold <= 0 || threshold >= 1) {
            throw new IllegalStateException("carlink.contact.spam-filter.threshold must be between 0 and 1");
        }
        this.enabled = enabled;
        this.threshold = threshold;
    }

    /** Default threshold (0.8). */
    public NoteSpamFilter(boolean enabled) {
        this(enabled, 0.8);
    }

    /** Throws 400 {@value #ERROR_CODE} when the note is off-topic. */
    public void requireOnTopic(String note) {
        if (!enabled || note == null || note.isBlank()) {
            return;
        }
        double score = Model.CLASSIFIER.blockProbability(note);
        if (score >= threshold) {
            log.info("Alert note refused as off-topic (score {})", String.format(Locale.ROOT, "%.2f", score));
            throw new BadRequestException(ERROR_CODE, "The note does not seem to be about this car.");
        }
    }

    /** Whether the note would be refused (for tests and tooling). */
    boolean wouldRefuse(String note) {
        return note != null && !note.isBlank() && Model.CLASSIFIER.blockProbability(note) >= threshold;
    }

    /** Trained once, on first use, from the bundled examples. */
    private static final class Model {
        static final NoteClassifier CLASSIFIER = NoteClassifier.fromBundledExamples();
    }
}
