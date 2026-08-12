package com.dillon.starsectormarines.campaign;

/** Persisted Chronicle event discriminator. Append only; never reorder. */
public enum ChronicleEventType {
    CHAIN_OUTCOME,
    ACTIVE_CHAIN_RUMOR;

    private static final ChronicleEventType[] VALUES = values();

    public static ChronicleEventType fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
