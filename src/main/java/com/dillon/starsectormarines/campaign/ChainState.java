package com.dillon.starsectormarines.campaign;

/**
 * Persisted lifecycle of a political chain. Ordinals are serialized in
 * {@link CampaignState}{@code .chainState[]} — append only; never reorder.
 */
public enum ChainState {
    ACTIVE,
    RESOLVED,
    FAILED;

    private static final ChainState[] VALUES = values();

    public static ChainState fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
