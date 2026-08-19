package com.dillon.starsectormarines.campaign;

/** Persisted black-swan event discriminator. Append only. */
public enum CampaignEventType {
    NONE,
    CIVILIAN_RESCUE,
    DEFECTOR_ASYLUM,
    SILENT_COLONY;

    private static final CampaignEventType[] VALUES = values();

    public static CampaignEventType fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : NONE;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
