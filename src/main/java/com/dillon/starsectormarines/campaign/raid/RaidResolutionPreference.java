package com.dillon.starsectormarines.campaign.raid;

/**
 * How the player's own raid on a market is resolved, chosen per save ({@code raid-resolution.md}).
 *
 * <p>Separate from the Nexerelin invasion preference: a player may fight invasions by hand and let small raids
 * resolve. Ordinals are persisted on {@code CampaignState.raidResolutionPreference}: append only.
 */
public enum RaidResolutionPreference {
    /** Offer the choice every time. A new save, and a save from before the setting existed, starts here. */
    ASK,
    /** Fight the raid as a Marine Operations battle without asking. */
    MARINE_OPERATIONS,
    /** Leave the raid to the game's standard resolution (vanilla's, or Nexerelin's when it is installed). */
    STANDARD;

    /** The preference a saved ordinal names; anything unreadable is {@link #ASK}. */
    public static RaidResolutionPreference of(int ordinal) {
        RaidResolutionPreference[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ASK;
    }
}
