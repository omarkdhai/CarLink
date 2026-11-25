package com.carlink.notification.whatsapp;

import com.carlink.notification.sms.AlertSms;

/**
 * The two variables of the {@code carlink_alert} template: the reason line in
 * Derja (the same as the SMS) and the visitor's note exactly as typed.
 *
 * <p>WhatsApp refuses template variables that are empty, contain a line break
 * or a tab, or more than four spaces in a row, so the note is flattened to one
 * line. Unlike the SMS it is not shortened to 66 characters: WhatsApp carries
 * the whole note (the API already caps it at 500).</p>
 */
final class WhatsAppTemplateText {

    /** Stands in for a missing note: an empty variable is rejected. */
    static final String NO_NOTE = "—";
    static final int NOTE_MAX = 900;

    private WhatsAppTemplateText() {
    }

    static String reason(String reason) {
        return AlertSms.line(reason);
    }

    static String note(String note) {
        if (note == null) {
            return NO_NOTE;
        }
        String flat = note.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {4,}", "   ").strip();
        if (flat.isEmpty()) {
            return NO_NOTE;
        }
        if (flat.length() > NOTE_MAX) {
            int end = NOTE_MAX;
            if (Character.isHighSurrogate(flat.charAt(end - 1))) {
                end--;                                   // never cut an emoji in half
            }
            flat = flat.substring(0, end) + "…";
        }
        return flat;
    }
}
