package com.dillon.starsectormarines.campaign.nexerelin;

/**
 * Where one Nexerelin invasion goes when the player selects Invade: the pure decision behind the intercepting
 * rule ({@code nex-invasion-conquest.md}, slice 1).
 */
public enum InvasionRoute {
    /** Show the choice between the two. */
    FORK,
    /** Nexerelin's own invasion, untouched: the rule does not intercept at all. */
    NEXERELIN;

    /**
     * @param asks      whether the save's {@link InvasionForkSetting} is on
     * @param nexerelin whether Nexerelin is enabled
     * @param supported whether this entry point is one Marine Operations can fight. In the alpha that is only the
     *                  player's own invasion of a planet's market; a station, a joined battle or anything else is
     *                  always Nexerelin's
     */
    public static InvasionRoute choose(boolean asks, boolean nexerelin, boolean supported) {
        return asks && nexerelin && supported ? FORK : NEXERELIN;
    }
}
