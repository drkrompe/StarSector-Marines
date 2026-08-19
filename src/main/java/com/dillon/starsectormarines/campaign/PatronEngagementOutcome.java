package com.dillon.starsectormarines.campaign;

/** Persisted patron-engagement outcome discriminator. Append only. */
public enum PatronEngagementOutcome {
    COMPLETED,
    FAILED,
    WITHDREW,
    EMPLOYER_BREACHED;

    private static final PatronEngagementOutcome[] VALUES = values();

    public static PatronEngagementOutcome fromByte(byte value) {
        return VALUES[value & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
