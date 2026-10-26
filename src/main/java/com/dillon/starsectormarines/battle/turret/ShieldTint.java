package com.dillon.starsectormarines.battle.turret;

/**
 * The colour a fitted flux shield is drawn in, by the technology that built it: blue for the high-tech field
 * emitter, teal for a midline one, orange for a low-tech one. A boat's emitter tier picks its tint.
 */
public enum ShieldTint {
    BLUE("blue"),
    TEAL("teal"),
    ORANGE("orange");

    public final String key;

    ShieldTint(String key) {
        this.key = key;
    }

    /** The tint named {@code key}, or null when none is. */
    public static ShieldTint byKey(String key) {
        for (ShieldTint tint : values()) if (tint.key.equals(key)) return tint;
        return null;
    }
}
