package com.dillon.starsectormarines.campaign;

/** Stable mission identity for one committed civilian-rescue event. */
public final class CivilianRescueMissionKey {

    private static final String PREFIX = "civilian-rescue:";

    public final long eventId;

    private CivilianRescueMissionKey(long eventId) {
        this.eventId = eventId;
    }

    public static String encode(long eventId) {
        return eventId > 0L ? PREFIX + eventId : null;
    }

    public static CivilianRescueMissionKey parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        try {
            long eventId = Long.parseLong(value.substring(PREFIX.length()));
            return eventId > 0L ? new CivilianRescueMissionKey(eventId) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
