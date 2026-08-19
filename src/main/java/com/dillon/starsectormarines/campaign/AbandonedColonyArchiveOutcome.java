package com.dillon.starsectormarines.campaign;

/** Persisted archive result for a Silent Colony expedition. Append only. */
public enum AbandonedColonyArchiveOutcome {
    NONE,
    LOST,
    RECOVERED;

    private static final AbandonedColonyArchiveOutcome[] VALUES = values();

    public static AbandonedColonyArchiveOutcome fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : NONE;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
