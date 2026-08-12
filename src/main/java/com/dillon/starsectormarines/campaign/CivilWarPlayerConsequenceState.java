package com.dillon.starsectormarines.campaign;

/** Exactly-once lifecycle for terminal civil-war player-reputation effects. */
public enum CivilWarPlayerConsequenceState {
    PENDING,
    APPLIED,
    NOT_APPLICABLE;

    private static final CivilWarPlayerConsequenceState[] VALUES = values();

    public static CivilWarPlayerConsequenceState fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : PENDING;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
