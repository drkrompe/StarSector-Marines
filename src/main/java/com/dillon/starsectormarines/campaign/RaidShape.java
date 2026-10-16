package com.dillon.starsectormarines.campaign;

/**
 * Which vanilla machinery a raid that armed a Garrison defence came through, so the
 * company's defence report can name what came down. The attacker faction is carried
 * separately; this is only the kind of operation.
 *
 * <p>Backed by {@link #ordinal()} into {@link CampaignState}{@code .contractDefenseRaidShape[]}
 * — append only, never reorder.
 */
public enum RaidShape {
    /** Not a vanilla raid, or not recorded. */
    UNKNOWN,
    /** A {@code RaidIntel} raid: pirate and Pathers base raids and their kin. */
    RAID,
    /** A {@code PunitiveExpeditionIntel}: one faction punishing a colony. */
    PUNITIVE_EXPEDITION,
    /** A {@code GenericRaidFGI} fleet group: hostile activity and faction expeditions. */
    FLEET_GROUP;

    private static final RaidShape[] VALUES = values();

    public static RaidShape fromByte(byte b) {
        int index = b & 0xFF;
        return index < VALUES.length ? VALUES[index] : UNKNOWN;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
