package com.dillon.starsectormarines.campaign.nexerelin;

/**
 * Where one Nexerelin invasion goes when the player selects Invade: the pure decision behind the intercepting
 * rule ({@code nex-invasion-conquest.md}, slice 1).
 */
public enum InvasionRoute {
    /** Show the choice between the two. */
    FORK,
    /** Straight to the Marine Operations invasion briefing. */
    MARINE_OPERATIONS,
    /** Nexerelin's own invasion, untouched: the rule does not intercept at all. */
    NEXERELIN;

    /**
     * @param preference the save's preference
     * @param nexerelin  whether Nexerelin is enabled
     * @param supported  whether this entry point is one Marine Operations can fight. In the alpha that is only the
     *                   player's own invasion of a planet's market; a station, a joined battle or anything else is
     *                   always Nexerelin's
     */
    public static InvasionRoute choose(GroundCombatPreference preference, boolean nexerelin, boolean supported) {
        if (!nexerelin || !supported) return NEXERELIN;
        if (preference == null) preference = GroundCombatPreference.ASK;
        return switch (preference) {
            case ASK -> FORK;
            case MARINE_OPERATIONS -> MARINE_OPERATIONS;
            case NEXERELIN -> NEXERELIN;
        };
    }
}
