package com.dillon.starsectormarines.campaign;

/** Persisted delivery lifecycle for a sealed kingmaker testimony snapshot. */
public enum KingmakerTestamentState {
    NONE,
    SEALED,
    REVEALED;

    private static final KingmakerTestamentState[] VALUES = values();

    public static KingmakerTestamentState fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : NONE;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
