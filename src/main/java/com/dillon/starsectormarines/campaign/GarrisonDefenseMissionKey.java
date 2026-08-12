package com.dillon.starsectormarines.campaign;

/** Stable identity carried from a pending Garrison defense through battle results. */
public final class GarrisonDefenseMissionKey {

    private static final String PREFIX = "garrison-defense:";

    public final long contractId;
    public final long eventKey;

    private GarrisonDefenseMissionKey(long contractId, long eventKey) {
        this.contractId = contractId;
        this.eventKey = eventKey;
    }

    public static String encode(GarrisonDefensePayload payload) {
        if (payload == null) return null;
        return PREFIX + payload.contractId + ":" + payload.eventKey;
    }

    public static GarrisonDefenseMissionKey parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        String[] parts = value.substring(PREFIX.length()).split(":", -1);
        if (parts.length != 2) return null;
        try {
            long contractId = Long.parseLong(parts[0]);
            long eventKey = Long.parseLong(parts[1]);
            if (eventKey == 0L) return null;
            return new GarrisonDefenseMissionKey(contractId, eventKey);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
