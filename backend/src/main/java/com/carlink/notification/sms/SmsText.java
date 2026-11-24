package com.carlink.notification.sms;

import java.util.Map;

/**
 * Fits an SMS body into one message.
 *
 * <p>A single SMS holds 160 characters of the GSM 03.38 alphabet (characters of
 * the extension table count twice), or only 70 (66 in practice, see below) as soon as any other character
 * appears (Arabic, emoji: the whole message switches to UCS-2). TunisieSMS
 * rejects a longer body (code 440), so the text is cut to fit and marked with
 * "..." — the owner reads the full note in the dashboard.</p>
 *
 * <p>French accents missing from the GSM alphabet (ê, ç, â, î, ô, û, ë, ï, œ…)
 * and typographic punctuation are first replaced by their closest GSM
 * character, so a French note keeps 160 characters instead of dropping to 70.
 * The frontend mirrors these rules ({@code frontend/src/lib/sms.ts}) to show
 * the visitor how much of the note fits.</p>
 */
public final class SmsText {

    public static final int GSM_LIMIT = 160;
    /**
     * A Unicode SMS is 70 UTF-16 units in theory, but TunisieSMS / the operator
     * REJECT longer than ~67 (measured 2026-11-24: 67 accepted, 69 and 70
     * rejected within a second, with no API error — the alert looked "sent").
     * 66 keeps one unit of margin below the highest accepted length.
     */
    public static final int UNICODE_LIMIT = 66;

    private static final String GSM_BASIC =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
                    + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
    private static final String GSM_EXTENSION = "^{}\\[~]|€\f";

    /** Non-GSM Latin characters and their GSM stand-in (keep in sync with sms.ts). */
    private static final Map<Character, String> TO_GSM = Map.ofEntries(
            Map.entry('â', "a"), Map.entry('á', "a"), Map.entry('ã', "a"),
            Map.entry('ê', "e"), Map.entry('ë', "e"),
            Map.entry('î', "i"), Map.entry('ï', "i"), Map.entry('í', "i"),
            Map.entry('ô', "o"), Map.entry('ó', "o"), Map.entry('õ', "o"),
            Map.entry('û', "u"), Map.entry('ú', "u"),
            Map.entry('ç', "c"), Map.entry('ÿ', "y"),
            Map.entry('À', "A"), Map.entry('Â', "A"), Map.entry('Á', "A"),
            Map.entry('È', "E"), Map.entry('Ê', "E"), Map.entry('Ë', "E"),
            Map.entry('Î', "I"), Map.entry('Ï', "I"), Map.entry('Ô', "O"),
            Map.entry('Ù', "U"), Map.entry('Û', "U"),
            Map.entry('œ', "oe"), Map.entry('Œ', "OE"),
            Map.entry('’', "'"), Map.entry('‘', "'"), Map.entry('ʼ', "'"),
            Map.entry('“', "\""), Map.entry('”', "\""), Map.entry('«', "\""), Map.entry('»', "\""),
            Map.entry('–', "-"), Map.entry('—', "-"), Map.entry('…', "..."),
            Map.entry(' ', " "), Map.entry(' ', " "), Map.entry('\t', " "));

    private SmsText() {
    }

    /** Replaces the characters listed in {@link #TO_GSM}; everything else is kept. */
    public static String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String replacement = TO_GSM.get(c);
            out.append(replacement != null ? replacement : String.valueOf(c));
        }
        return out.toString();
    }

    public static boolean isGsm(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (GSM_BASIC.indexOf(c) < 0 && GSM_EXTENSION.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Length as the network counts it (septets for GSM, UTF-16 units otherwise). */
    public static int length(String text, boolean gsm) {
        if (!gsm) {
            return text.length();
        }
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            n += GSM_EXTENSION.indexOf(text.charAt(i)) >= 0 ? 2 : 1;
        }
        return n;
    }

    /**
     * Shortens {@code text} with "..." if it does not fit one SMS. The accent
     * replacement is applied only when it lets the whole text stay in the GSM
     * alphabet (160); a Unicode SMS (Arabic, emoji) keeps every character as
     * written.
     */
    public static String fit(String text) {
        String normalized = normalize(text);
        boolean gsm = isGsm(normalized);
        String chosen = gsm ? normalized : text;
        int limit = gsm ? GSM_LIMIT : UNICODE_LIMIT;
        if (length(chosen, gsm) <= limit) {
            return chosen;
        }
        String cut = chosen;
        while (!cut.isEmpty() && length(cut, gsm) > limit - 3) {
            cut = cut.substring(0, cut.length() - 1);
        }
        // Never leave half of a surrogate pair (emoji) at the end.
        if (!cut.isEmpty() && Character.isHighSurrogate(cut.charAt(cut.length() - 1))) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.stripTrailing() + "...";
    }
}
