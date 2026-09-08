package com.carlink.qr.dto;

import java.util.List;

/**
 * What the public page may reveal about a QR token. Deliberately minimal:
 * a safe vehicle summary (no license plate, no phone, no owner identity)
 * plus the advertised contact channels.
 */
public record QrPublicView(
        VehicleSafe vehicle,
        List<String> channels
) {

    /** Owner-safe vehicle fields settable at registration time. */
    public record VehicleSafe(String nickname, String brand, String model, String color) {}
}