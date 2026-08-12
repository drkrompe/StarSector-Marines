package com.dillon.starsectormarines.campaign;

/** Persisted certainty attached to a learned Chronicle event. Never reorder. */
public enum ChronicleConfidence {
    /** Confirmed outcome; ordinal zero also safely backfills legacy D2 rows. */
    CONFIRMED,
    /** In-flight political activity whose eventual outcome is still unknown. */
    RUMOR;

    private static final ChronicleConfidence[] VALUES = values();

    public static ChronicleConfidence fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
