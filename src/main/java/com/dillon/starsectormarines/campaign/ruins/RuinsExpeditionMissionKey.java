package com.dillon.starsectormarines.campaign.ruins;

/** The mission id of a ruins expedition: its settlement serial and the ruins world's market id. */
public record RuinsExpeditionMissionKey(int serial, String marketId) {

    private static final String PREFIX = "ruins-expedition:";

    public static String encode(int serial, String marketId) {
        return PREFIX + serial + ":" + marketId;
    }

    /** The key a mission id carries, or null when it is not an expedition's. The market id may itself hold colons. */
    public static RuinsExpeditionMissionKey parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        String rest = value.substring(PREFIX.length());
        int split = rest.indexOf(':');
        if (split <= 0 || split == rest.length() - 1) return null;
        try {
            return new RuinsExpeditionMissionKey(Integer.parseInt(rest.substring(0, split)), rest.substring(split + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
