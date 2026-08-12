package com.dillon.starsectormarines.campaign;

/** Persisted Cadre incident archetype; append new values to preserve save ordinals. */
public enum StationingIncidentType {
    NONE,
    FACTORY_ACCIDENT,
    LIVE_FIRE_RAID,
    DEFECTOR_LEAD;

    private static final StationingIncidentType[] VALUES = values();

    public static StationingIncidentType fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : NONE;
    }

    public byte toByte() {
        return (byte) ordinal();
    }

    public static StationingIncidentType pick(long contractId, int dueDay) {
        long mixed = contractId * 0xD6E8FEB86659FD93L
                ^ (long) dueDay * 0xA5A3564E27F8862BL;
        mixed ^= mixed >>> 32;
        int index = 1 + (int) Math.floorMod(mixed, (long) (VALUES.length - 1));
        return VALUES[index];
    }
}
