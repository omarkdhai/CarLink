package com.carlink.qr.dto;

import java.util.List;

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
 */
public record QrPublicView(
        String state,
        VehicleSafe vehicle,
        List<String> channels
) {

    /** Owner-safe vehicle fields settable at registration time. */
    public record VehicleSafe(String nickname, String brand, String model, String color) {}
}
