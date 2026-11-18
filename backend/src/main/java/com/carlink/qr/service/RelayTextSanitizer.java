package com.carlink.qr.service;

import java.util.regex.Pattern;

/**
 * Cleans the free-text note a visitor attaches to an alert before it is stored
 * and relayed to the owner by SMS (security plan S-12).
 *
 * <p>CarLink delivers that note from a trusted sender to a phone number the
 * visitor cannot see, which makes it an attractive phishing channel ("your car
 * was towed, pay here: …"). Links have no legitimate use in a parking alert, so
 * anything that looks like one is replaced by a neutral marker.
 */
public final class RelayTextSanitizer {

    static final String LINK_MARKER = "[link removed]";

    /**
     * Label separator: a plain dot with no spaces (so "my car. Me and…" is not a
     * link), or an obfuscated one where spaces are typical: "[.]", "(.)", " dot ".
     */
    private static final String DOT = "(?:\\.|\\s*\\[\\.\\]\\s*|\\s*\\(\\.\\)\\s*|\\s+dot\\s+)";

    /** Scheme URLs, www. hosts, and bare domains on common or abused TLDs. */
    private static final Pattern LINK = Pattern.compile(
            "(?i)(?:\\b(?:https?|ftp)://\\S+"
                    + "|\\bwww" + DOT + "\\S+"
                    + "|\\b[a-z0-9][a-z0-9-]{0,62}(?:" + DOT + "[a-z0-9-]{1,63})*" + DOT
                    + "(?:com|net|org|info|biz|io|co|me|ly|tn|fr|app|link|click|top|site|online|shop|xyz|ru|cn|tk|gq|ml|live|page|gl|to)"
                    + "\\b(?:/\\S*)?)");

    private RelayTextSanitizer() {
    }

    /** Returns the note with every link-like fragment replaced; null stays null. */
    public static String neutraliseLinks(String text) {
        if (text == null) {
            return null;
        }
        return LINK.matcher(text).replaceAll(LINK_MARKER);
    }
}
