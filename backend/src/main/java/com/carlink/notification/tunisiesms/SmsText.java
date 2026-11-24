package com.carlink.notification.tunisiesms;

/**
 * Fits an SMS body into one message.
 *
 * <p>A single SMS holds 160 characters of the GSM 03.38 alphabet (characters of
 * the extension table count twice), or only 70 as soon as any other character
 * appears (Arabic, emoji, curly quotes: the whole message switches to UCS-2).
 * TunisieSMS rejects a longer body with code 440, so the text is cut to fit and
 * marked with "..." — the owner reads the full note in the dashboard.</p>
 */
final class SmsText {

    static final int GSM_LIMIT = 160;
    static final int UNICODE_LIMIT = 70;

    private static final String GSM_BASIC =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
                    + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
    private static final String GSM_EXTENSION = "^{}\\[~]|€\f";

    private SmsText() {
    }

    static boolean isGsm(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (GSM_BASIC.indexOf(c) < 0 && GSM_EXTENSION.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Length as the network counts it (septets for GSM, UTF-16 units otherwise). */
    static int length(String text, boolean gsm) {
        if (!gsm) {
            return text.length();
        }
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            n += GSM_EXTENSION.indexOf(text.charAt(i)) >= 0 ? 2 : 1;
        }
        return n;
    }

    /** Returns {@code text}, shortened with "..." if it does not fit one SMS. */
    static String fit(String text) {
        boolean gsm = isGsm(text);
        int limit = gsm ? GSM_LIMIT : UNICODE_LIMIT;
        if (length(text, gsm) <= limit) {
            return text;
        }
        String cut = text;
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
