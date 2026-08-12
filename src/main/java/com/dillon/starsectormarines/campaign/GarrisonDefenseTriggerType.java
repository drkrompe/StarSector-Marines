package com.dillon.starsectormarines.campaign;

/** Persisted source of a reactive Garrison defense; append values for save compatibility. */
public enum GarrisonDefenseTriggerType {
    NONE,
    RIVAL_STRIKE,
    VANILLA_RAID,
    INTERNAL_FLIP;

    private static final GarrisonDefenseTriggerType[] VALUES = values();

    public static GarrisonDefenseTriggerType fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : NONE;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
