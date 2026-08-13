package com.dillon.starsectormarines.campaign;

/** Persisted terminal outcome for a defector-asylum event. Append only. */
public enum DefectorAsylumOutcome {
    NONE,
    PROTECTED,
    BETRAYED;

    private static final DefectorAsylumOutcome[] VALUES = values();

    public static DefectorAsylumOutcome fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : NONE;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
