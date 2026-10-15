package com.dillon.starsectormarines.campaign.nexerelin;

/**
 * The mission id of a Marine Operations invasion: its settlement serial and the invaded market's id. Settlement reads
 * the market back from it, so nothing about the market is kept anywhere else.
 */
public record NexInvasionMissionKey(int serial, String marketId) {

    private static final String PREFIX = "nex-invasion:";

    public static String encode(int serial, String marketId) {
        return PREFIX + serial + ":" + marketId;
    }

    /** The key a mission id carries, or null when it is not an invasion's. The market id may itself hold colons. */
    public static NexInvasionMissionKey parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        String rest = value.substring(PREFIX.length());
        int split = rest.indexOf(':');
        if (split <= 0 || split == rest.length() - 1) return null;
        try {
            return new NexInvasionMissionKey(Integer.parseInt(rest.substring(0, split)), rest.substring(split + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
