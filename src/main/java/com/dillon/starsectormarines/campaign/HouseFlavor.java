package com.dillon.starsectormarines.campaign;

/**
 * Cultural / political flavor of a sub-faction house. Drives vocabulary in UI
 * (Baron vs Manager vs Capo vs Cell Leader) and biases which mission archetypes
 * a house tends to offer.
 *
 * <p>Backed by {@link #ordinal()} into the {@code byte} slot in
 * {@link CampaignState}{@code .houseFlavor[]} — never reorder.
 */
public enum HouseFlavor {
    CORPORATE,
    FEUDAL,
    UNDERWORLD,
    SECTARIAN;

    private static final HouseFlavor[] VALUES = values();

    public static HouseFlavor fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
