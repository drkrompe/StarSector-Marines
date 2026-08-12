package com.dillon.starsectormarines.campaign;

/** Persisted choice/outcome lifecycle for black-swan events. Append only. */
public enum CampaignEventState {
    PENDING_CHOICE,
    COMMITTED,
    REFUSED,
    RESOLVED,
    EXPIRED;

    private static final CampaignEventState[] VALUES = values();

    public static CampaignEventState fromByte(byte value) {
        int index = value & 0xFF;
        return index < VALUES.length ? VALUES[index] : PENDING_CHOICE;
    }

    public boolean isTerminal() {
        return this == REFUSED || this == RESOLVED || this == EXPIRED;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
