package com.dillon.starsectormarines.campaign;

/**
 * Shape of a multi-step political play. A chain is the CK3-scheme analog: a
 * patron hires the player to drive a particular kind of outcome over multiple
 * missions.
 *
 * <p>Backed by {@link #ordinal()} into the {@code byte} slot in
 * {@link CampaignState}{@code .chainArchetype[]} — never reorder.
 */
public enum ChainArchetype {
    CONSOLIDATE_STAKE,
    SABOTAGE_PROMOTION,
    ELEVATE_HEIR,
    CIVIL_WAR;

    private static final ChainArchetype[] VALUES = values();

    public static ChainArchetype fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
