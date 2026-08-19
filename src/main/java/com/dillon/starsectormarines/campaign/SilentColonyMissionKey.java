package com.dillon.starsectormarines.campaign;

/** Stable mission identity for one committed Silent Colony expedition. */
public final class SilentColonyMissionKey {

    private static final String PREFIX = "silent-colony:";

    public final long eventId;

    private SilentColonyMissionKey(long eventId) {
        this.eventId = eventId;
    }

    public static String encode(long eventId) {
        return eventId > 0L ? PREFIX + eventId : null;
    }

    public static SilentColonyMissionKey parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        try {
            long eventId = Long.parseLong(value.substring(PREFIX.length()));
            return eventId > 0L ? new SilentColonyMissionKey(eventId) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
