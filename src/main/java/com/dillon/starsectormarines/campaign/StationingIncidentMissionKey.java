package com.dillon.starsectormarines.campaign;

/** Stable identity carried from an armed Cadre incident through battle results. */
public final class StationingIncidentMissionKey {

    private static final String PREFIX = "cadre-incident:";

    public final long contractId;
    public final int dueDay;
    public final StationingIncidentType type;

    private StationingIncidentMissionKey(long contractId, int dueDay,
                                         StationingIncidentType type) {
        this.contractId = contractId;
        this.dueDay = dueDay;
        this.type = type;
    }

    public static String encode(StationingIncidentPayload payload) {
        if (payload == null) return null;
        return PREFIX + payload.contractId + ":" + payload.dueDay + ":" + payload.type.name();
    }

    public static StationingIncidentMissionKey parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        String[] parts = value.substring(PREFIX.length()).split(":", -1);
        if (parts.length != 3) return null;
        try {
            long contractId = Long.parseLong(parts[0]);
            int dueDay = Integer.parseInt(parts[1]);
            StationingIncidentType type = StationingIncidentType.valueOf(parts[2]);
            if (type == StationingIncidentType.NONE) return null;
            return new StationingIncidentMissionKey(contractId, dueDay, type);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
