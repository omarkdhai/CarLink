package com.carlink.qr.model;

/**
 * The fixed set of alert reasons a passenger can pick when scanning an owner's
 * QR. The value is stored on the {@code messages.reason} column; the label is
 * the human-readable text sent to the owner (SMS + dashboard) when the
 * passenger leaves no custom note.
 */
public enum ContactReason {

    BLOCKING("Your car is blocking me"),
    LIGHTS("Your lights are on"),
    LEFT_OPEN("Your car is left open"),
    HIT_CAR("I accidentally hit your car"),
    WINDOWS_OPEN("Your windows are left open"),
    EV_CHARGE("Your car is fully charged and I need to charge my EV");

    private final String label;

    ContactReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ContactReason from(String value) {
        for (ContactReason r : values()) {
            if (r.name().equals(value)) return r;
        }
        throw new IllegalArgumentException("Unknown contact reason: " + value);
    }
}