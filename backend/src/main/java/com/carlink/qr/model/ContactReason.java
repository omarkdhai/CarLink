package com.carlink.qr.model;

/**
 * The fixed set of alert reasons a passenger can pick when scanning an owner's
 * QR. The value is stored on the {@code messages.reason} column; the label is
 * the human-readable text sent to the owner (SMS + dashboard) when the
 * passenger leaves no custom note.
 */
public enum ContactReason {

    BLOCKING("Your car is blocking mine"),
    LIGHTS("Your headlights are still on"),
    LEFT_OPEN("Your car has been left unlocked"),
    HIT_CAR("I accidentally bumped into your car"),
    WINDOWS_OPEN("Your windows have been left open"),
    CHILD_OR_PET("A child or an animal is locked in your car"),
    /** Needs a note: the visitor must say what the problem is. */
    OTHER("Another problem with your car");

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