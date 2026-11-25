package com.carlink.notification.sms;

import com.carlink.qr.model.ContactReason;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The text of the SMS alert sent to an owner. Always in Tunisian Derja:
 *
 * <pre>
 * 💡 ضو كرهبتك شاعل
 * 💬 &lt;the visitor's note, exactly as typed, in any language&gt;
 * </pre>
 *
 * <p>The first line is the problem the visitor picked, the second their note,
 * untouched (only shortened if it does not fit). The sender name already reads
 * "CarLink", so the text does not repeat it.</p>
 *
 * <p>Arabic letters and emoji make every alert a Unicode SMS: 70 characters in
 * all, an emoji counting as 2. Every reason line stays short enough to leave
 * room for a note ({@link #noteLimits()}, shown to the visitor while typing).
 * The emoji are single code points from early Unicode versions (no variation
 * selectors), so they display on old phones too.</p>
 */
public final class AlertSms {

    private static final Map<ContactReason, String> LINES = new EnumMap<>(ContactReason.class);

    static {
        LINES.put(ContactReason.BLOCKING, "🚧 كرهبتك سادّة عليّ");
        LINES.put(ContactReason.LIGHTS, "💡 ضو كرهبتك شاعل");
        LINES.put(ContactReason.LEFT_OPEN, "🔓 كرهبتك محلولة");
        LINES.put(ContactReason.HIT_CAR, "💥 ضربت كرهبتك بلاش ما نقصد");
        LINES.put(ContactReason.WINDOWS_OPEN, "☔ شبابك كرهبتك محلولين");
        LINES.put(ContactReason.EV_CHARGE, "🔋 كرهبتك شحنت، نحب نشحن كرهبتي");
    }

    /** Unknown reason (should not happen: the API validates it). */
    private static final String FALLBACK = "❗ فما شكون يحب يكلمك";
    private static final String NOTE_PREFIX = "💬 ";

    private AlertSms() {
    }

    /** The reason line, in Derja with its emoji. */
    public static String line(String reason) {
        try {
            String line = LINES.get(ContactReason.from(reason));
            return line == null ? FALLBACK : line;
        } catch (IllegalArgumentException | NullPointerException e) {
            return FALLBACK;
        }
    }

    private static String head(String reason) {
        return line(reason) + "\n" + NOTE_PREFIX;
    }

    /** The full SMS, fitted to one message. */
    public static String compose(String reason, String note) {
        String trimmed = note == null ? "" : note.strip();
        String text = trimmed.isEmpty() ? line(reason) : head(reason) + trimmed;
        return SmsText.fit(text);
    }

    /**
     * Characters of the visitor's note that fit in the SMS, per reason. The
     * SMS is always Unicode (Derja), so this is {@link SmsText#UNICODE_LIMIT} (66) minus the reason line, an
     * emoji in the note counting as 2 (UTF-16 units, as the network counts).
     */
    public static Map<String, Integer> noteLimits() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        for (ContactReason reason : ContactReason.values()) {
            limits.put(reason.name(), Math.max(0, SmsText.UNICODE_LIMIT - head(reason.name()).length()));
        }
        return limits;
    }
}
