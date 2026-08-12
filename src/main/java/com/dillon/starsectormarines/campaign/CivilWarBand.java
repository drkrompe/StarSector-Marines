package com.dillon.starsectormarines.campaign;

/** Civil-war phase captured when a participation contract is offered. */
public enum CivilWarBand {
    NONE(0),
    COALITION_BUILDING(15),
    MOBILIZATION(30),
    OPEN_CONFLICT(0);

    private static final CivilWarBand[] VALUES = values();

    public final int contributionWeight;

    CivilWarBand(int contributionWeight) {
        this.contributionWeight = contributionWeight;
    }

    public static CivilWarBand fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : NONE;
    }

    public static CivilWarBand forProgress(int progress) {
        if (progress < 0) return NONE;
        if (progress < 60) return COALITION_BUILDING;
        if (progress < 120) return MOBILIZATION;
        return OPEN_CONFLICT;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
