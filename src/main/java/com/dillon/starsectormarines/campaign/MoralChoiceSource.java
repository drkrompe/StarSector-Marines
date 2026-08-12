package com.dillon.starsectormarines.campaign;

/** Persisted source namespace for hidden moral-compass choices. Append only. */
public enum MoralChoiceSource {
    NONE,
    CIVIL_WAR_CLAIMANT,
    CIVIL_WAR_INCUMBENT;

    private static final MoralChoiceSource[] VALUES = values();

    public static MoralChoiceSource fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : NONE;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
