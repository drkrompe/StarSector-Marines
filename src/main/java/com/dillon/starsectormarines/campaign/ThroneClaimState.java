package com.dillon.starsectormarines.campaign;

/** Persisted lifecycle of an irreversible Tier-3 endgame handoff. */
public enum ThroneClaimState {
    PREPARED,
    APPLIED,
    FAILED;

    private static final ThroneClaimState[] VALUES = values();

    public static ThroneClaimState fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
