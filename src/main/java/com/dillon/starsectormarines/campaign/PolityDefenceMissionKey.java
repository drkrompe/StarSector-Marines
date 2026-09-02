package com.dillon.starsectormarines.campaign;

/**
 * Stable identity for a polity defence fought on the player's own colony.
 *
 * <p>A Garrison defence is keyed by the contract that stationed the detachment; a polity
 * defence has no contract, so the market it is fought on stands in its place. The pair
 * (market, raid) is what the resolution must settle exactly once.
 */
public final class PolityDefenceMissionKey {

    private static final String PREFIX = "polity-defence:";

    /** {@code CampaignState.marketRegistry} slot of the defended colony. */
    public final int marketSlot;
    public final long eventKey;

    private PolityDefenceMissionKey(int marketSlot, long eventKey) {
        this.marketSlot = marketSlot;
        this.eventKey = eventKey;
    }

    public static String encode(int marketSlot, long eventKey) {
        return PREFIX + marketSlot + ":" + eventKey;
    }

    /** Returns {@code null} for anything that is not one of our own encoded keys. */
    public static PolityDefenceMissionKey parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        String[] parts = value.substring(PREFIX.length()).split(":", -1);
        if (parts.length != 2) return null;
        try {
            int marketSlot = Integer.parseInt(parts[0]);
            long eventKey = Long.parseLong(parts[1]);
            if (eventKey == 0L) return null;
            return new PolityDefenceMissionKey(marketSlot, eventKey);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
