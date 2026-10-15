package com.dillon.starsectormarines.campaign;

/** Persisted Chronicle event discriminator. Append only; never reorder. */
public enum ChronicleEventType {
    CHAIN_OUTCOME,
    ACTIVE_CHAIN_RUMOR,
    HOUSE_DORMANT,
    THRONE_CLAIM_APPLIED,
    KINGMAKER_TESTAMENT,
    SILENT_COLONY,
    /** A Garrison defence settled without the player; see {@code contracts-nouns.md} (stranded detachments). */
    GARRISON_DEFENCE,
    /** A stranded detachment was collected by the company. */
    DETACHMENT_RECOVERED,
    /** A stranded detachment's recovery ran out before anyone came. */
    DETACHMENT_LOST,
    /** A Garrison's market changed hands and its detachment went to ground. */
    GARRISON_MARKET_LOST;

    private static final ChronicleEventType[] VALUES = values();

    public static ChronicleEventType fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
