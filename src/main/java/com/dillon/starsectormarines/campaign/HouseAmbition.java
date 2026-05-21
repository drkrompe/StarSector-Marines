package com.dillon.starsectormarines.campaign;

/**
 * What a house is currently angling for. Drives the autonomous tick — a
 * {@code CONSOLIDATE_STAKE} house accrues promotion progress when it absorbs
 * industry shares; a {@code DISPLACE_RIVAL} house spends political capital
 * shoving someone down a rank; etc.
 *
 * <p>Backed by {@link #ordinal()} into the {@code byte} slot in
 * {@link CampaignState}{@code .houseAmbition[]} — never reorder.
 */
public enum HouseAmbition {
    NONE,
    CONSOLIDATE_STAKE,
    DISPLACE_RIVAL,
    PROMOTE,
    CLAIM_THRONE;

    private static final HouseAmbition[] VALUES = values();

    public static HouseAmbition fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
