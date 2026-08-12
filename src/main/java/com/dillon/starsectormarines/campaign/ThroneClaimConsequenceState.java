package com.dillon.starsectormarines.campaign;

/** Persisted lifecycle for post-writeback throne-claim consequences. */
public enum ThroneClaimConsequenceState {
    PENDING,
    APPLIED,
    FAILED;

    private static final ThroneClaimConsequenceState[] VALUES = values();

    public static ThroneClaimConsequenceState fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : PENDING;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
