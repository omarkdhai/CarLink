package com.carlink.qr.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small text classifier, built into the backend, that tells whether an alert
 * note is about the car (or a neutral courtesy) or not (ads, scams, dating,
 * insults, unrelated chat, gibberish).
 *
 * <p>Logistic regression over character fragments and words, trained at startup
 * from the labelled notes in {@value #TRAINING_FILE}. No library, no model file,
 * no outside service: the note never leaves the server, and nothing has to be
 * paid for or kept running. Character fragments make it work on Tunisian Derja
 * in Arabic and Latin letters, Arabic, French and English, mixed in one note,
 * and on spelling variants it has never seen.</p>
 *
 * <p>Training is deterministic (fixed seed), so the same file always gives the
 * same model.</p>
 */
public final class NoteClassifier {

    public static final String TRAINING_FILE = "spam/notes-train.tsv";

    private static final int EPOCHS = 60;
    private static final double LEARNING_RATE = 0.5;
    private static final double L2 = 1e-4;
    private static final long SEED = 42L;

    private static final Pattern ARABIC_MARKS = Pattern.compile("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]");
    private static final Pattern COMBINING = Pattern.compile("\\p{M}+");
    private static final Pattern REPEATS = Pattern.compile("(.)\\1{2,}");
    private static final Pattern EMAIL = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern SYMBOLS = Pattern.compile("[^\\p{L}\\p{N}\\s]+");
    private static final Pattern DIGITS = Pattern.compile("\\p{N}+");
    private static final Pattern ARABIC = Pattern.compile("\\p{IsArabic}");
    private static final String[] ARABIC_PREFIXES = {"وال", "بال", "فال", "لل", "ال", "و", "ف", "ب", "ل"};

    private final Map<String, Double> weights;
    private final double bias;

    private NoteClassifier(Map<String, Double> weights, double bias) {
        this.weights = weights;
        this.bias = bias;
    }

    /** One labelled note: {@code blocked} = not about the car. */
    public record Example(String note, boolean blocked) {
    }

    // ---------------------------------------------------------------- use

    /** Probability (0..1) that the note is not about the car. */
    public double blockProbability(String note) {
        double z = bias;
        for (Map.Entry<String, Double> f : features(note).entrySet()) {
            Double w = weights.get(f.getKey());
            if (w != null) {
                z += w * f.getValue();
            }
        }
        return sigmoid(z);
    }

    // ---------------------------------------------------------------- training

    /** Trains on the bundled file ({@value #TRAINING_FILE}). */
    public static NoteClassifier fromBundledExamples() {
        return train(load(TRAINING_FILE));
    }

    /** Stochastic gradient descent, classes weighted so both count equally. */
    public static NoteClassifier train(List<Example> examples) {
        if (examples.isEmpty()) {
            throw new IllegalArgumentException("No training examples");
        }
        List<Map<String, Double>> xs = new ArrayList<>(examples.size());
        for (Example e : examples) {
            xs.add(features(e.note()));
        }
        long blocked = examples.stream().filter(Example::blocked).count();
        long allowed = examples.size() - blocked;
        double weightBlocked = examples.size() / (2.0 * Math.max(1, blocked));
        double weightAllowed = examples.size() / (2.0 * Math.max(1, allowed));

        Map<String, Double> w = new HashMap<>();
        double b = 0;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < examples.size(); i++) {
            order.add(i);
        }
        Random random = new Random(SEED);
        for (int epoch = 0; epoch < EPOCHS; epoch++) {
            Collections.shuffle(order, random);
            double rate = LEARNING_RATE / (1 + 0.05 * epoch);
            for (int i : order) {
                Map<String, Double> x = xs.get(i);
                boolean y = examples.get(i).blocked();
                double z = b;
                for (Map.Entry<String, Double> f : x.entrySet()) {
                    z += w.getOrDefault(f.getKey(), 0.0) * f.getValue();
                }
                double gradient = (sigmoid(z) - (y ? 1 : 0)) * (y ? weightBlocked : weightAllowed);
                b -= rate * gradient;
                for (Map.Entry<String, Double> f : x.entrySet()) {
                    double old = w.getOrDefault(f.getKey(), 0.0);
                    w.put(f.getKey(), old - rate * (gradient * f.getValue() + L2 * old));
                }
            }
        }
        return new NoteClassifier(Map.copyOf(w), b);
    }

    /** Reads a {@code label<TAB>note} file from the classpath (A = allowed, B = blocked). */
    public static List<Example> load(String resource) {
        InputStream in = NoteClassifier.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException(resource + " is missing from the classpath");
        }
        List<Example> examples = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                int tab = line.indexOf('\t');
                String label = tab < 0 ? "" : line.substring(0, tab).trim();
                if (!label.equals("A") && !label.equals("B")) {
                    throw new IllegalStateException(resource + ":" + number + ": expected A or B, a tab, then the note");
                }
                examples.add(new Example(line.substring(tab + 1), label.equals("B")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return examples;
    }

    // ---------------------------------------------------------------- features

    /**
     * Lower case, no accents or Arabic diacritics, unified Arabic letters
     * (أ/إ/آ -> ا, ة -> ه, ى -> ي), and any letter repeated 3+ times cut to two.
     */
    static String normalise(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        s = ARABIC_MARKS.matcher(s).replaceAll("");
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
                .replace('ة', 'ه').replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي');
        s = COMBINING.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return REPEATS.matcher(s).replaceAll("$1$1");
    }

    /**
     * Words, word pairs, character fragments of 2 to 4 letters inside each word,
     * and runs of symbols; e-mail addresses and plain numbers become one token
     * each (a phone number says nothing about the topic). Scaled to unit length.
     */
    static Map<String, Double> features(String note) {
        Map<String, Double> f = new LinkedHashMap<>();
        String text = normalise(note == null ? "" : note);
        text = EMAIL.matcher(text).replaceAll(" xemailx ");

        List<String> words = new ArrayList<>();
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            String word = m.group();
            words.add(DIGITS.matcher(word).matches() ? "xnumx" : word);
        }
        String previous = "^";
        for (String word : words) {
            f.put("w:" + word, 1.0);
            f.put("b:" + previous + "_" + word, 1.0);
            previous = word;
            if (ARABIC.matcher(word).find()) {
                for (String prefix : ARABIC_PREFIXES) {
                    if (word.startsWith(prefix) && word.length() - prefix.length() >= 2) {
                        f.put("w:" + word.substring(prefix.length()), 1.0);
                        break;
                    }
                }
            }
            if (!word.equals("xnumx") && !word.equals("xemailx")) {
                String padded = "<" + word + ">";
                for (int n = 2; n <= 4; n++) {
                    for (int i = 0; i + n <= padded.length(); i++) {
                        f.put("c:" + padded.substring(i, i + n), 1.0);
                    }
                }
            }
        }
        Matcher s = SYMBOLS.matcher(text);
        while (s.find()) {
            f.put("s:" + s.group(), 1.0);
        }
        if (words.isEmpty()) {
            f.put("n:none", 1.0);                                // only symbols
        }

        double norm = Math.sqrt(f.size());
        f.replaceAll((k, v) -> v / norm);
        return f;
    }

    private static double sigmoid(double z) {
        return 1 / (1 + Math.exp(-z));
    }
}
