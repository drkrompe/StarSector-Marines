package com.dillon.starsectormarines.campaign.raid;

/**
 * Where the player's raid goes when they confirm it: the pure decision behind the intercepting rule
 * ({@code raid-resolution.md}). The same with or without Nexerelin, because the standard path is whichever raid
 * command the game is running.
 */
public enum RaidRoute {
    /** Show the choice between the two. */
    FORK,
    /** Straight to the Marine Operations raid briefing. */
    MARINE_OPERATIONS,
    /** The standard resolution, untouched: the rule does not intercept at all. */
    STANDARD;

    /**
     * @param preference the save's preference
     * @param supported  whether Marine Operations can fight this raid: a raid for valuables or to disrupt an
     *                   industry, on a planet's market. A station, a raid on something that is not a market, or a
     *                   mission's custom-only raid is always standard
     */
    public static RaidRoute choose(RaidResolutionPreference preference, boolean supported) {
        if (!supported) return STANDARD;
        if (preference == null) preference = RaidResolutionPreference.ASK;
        return switch (preference) {
            case ASK -> FORK;
            case MARINE_OPERATIONS -> MARINE_OPERATIONS;
            case STANDARD -> STANDARD;
        };
    }
}
