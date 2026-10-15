package com.dillon.starsectormarines.campaign;

/** Persisted Chronicle event discriminator. Append only; never reorder. */
public enum ChronicleEventType {
    CHAIN_OUTCOME,
    ACTIVE_CHAIN_RUMOR,
    HOUSE_DORMANT,
    THRONE_CLAIM_APPLIED,
    KINGMAKER_TESTAMENT,
    SILENT_COLONY,
    /** A Garrison defence settled without the player; see {@code stranded-detachment-recovery.md}. */
    GARRISON_DEFENCE;

    private static final ChronicleEventType[] VALUES = values();

    public static ChronicleEventType fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
