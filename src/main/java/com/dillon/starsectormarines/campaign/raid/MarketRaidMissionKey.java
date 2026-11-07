package com.dillon.starsectormarines.campaign.raid;

/**
 * The mission id of a Marine Operations raid on a market: its settlement serial and the raided market's id.
 */
public record MarketRaidMissionKey(int serial, String marketId) {

    private static final String PREFIX = "market-raid:";

    public static String encode(int serial, String marketId) {
        return PREFIX + serial + ":" + marketId;
    }

    /** The key a mission id carries, or null when it is not a market raid's. The market id may itself hold colons. */
    public static MarketRaidMissionKey parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        String rest = value.substring(PREFIX.length());
        int split = rest.indexOf(':');
        if (split <= 0 || split == rest.length() - 1) return null;
        try {
            return new MarketRaidMissionKey(Integer.parseInt(rest.substring(0, split)), rest.substring(split + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
