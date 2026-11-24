package com.carlink.qr.dto;

import java.util.List;
import java.util.Map;

/**
 * What the public page may reveal about a QR token. Deliberately minimal:
 * a safe vehicle summary (no license plate, no phone, no owner identity)
 * plus the advertised contact channels.
 *
 * <p>The {@code state} field supports the sticker lifecycle:</p>
 * <ul>
 *   <li>{@code BOUND} — the sticker is active; {@code vehicle} and {@code channels} are populated.</li>
 *   <li>{@code UNBOUND} — the sticker is virgin (not yet claimed); {@code vehicle} is null.</li>
 *   <li>{@code DEACTIVATED} — the owner released the sticker; {@code vehicle} is null.</li>
 * </ul>
 *
 * <p>Legacy {@code qr_codes} rows always resolve as {@code BOUND}.</p>
 *
 * <p>{@code smsNoteLimits} (BOUND only): per reason, how many characters of the
 * visitor's note fit in the SMS (the same for every owner), so the page can
 * count them down.</p>
 */
public record QrPublicView(
        String state,
        VehicleSafe vehicle,
        List<String> channels,
        Map<String, Integer> smsNoteLimits
) {

    /** Owner-safe vehicle fields settable at registration time. */
    public record VehicleSafe(String nickname, String brand, String model, String color) {}
}
