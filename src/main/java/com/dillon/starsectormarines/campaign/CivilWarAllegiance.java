package com.dillon.starsectormarines.campaign;

/** Player allegiance persisted on a civil-war chain after its first success. */
public enum CivilWarAllegiance {
    NONE,
    CLAIMANT,
    INCUMBENT;

    private static final CivilWarAllegiance[] VALUES = values();

    public static CivilWarAllegiance fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : NONE;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
