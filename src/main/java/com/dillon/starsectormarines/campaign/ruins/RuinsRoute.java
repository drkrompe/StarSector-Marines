package com.dillon.starsectormarines.campaign.ruins;

/**
 * Where the player's Explore the ruins goes: the pure decision behind the intercepting rule
 * ({@code ruins-expedition.md}).
 */
public enum RuinsRoute {
    /** Show the choice between the company and the salvage team. */
    FORK,
    /** Vanilla's salvage team, untouched: the rule does not intercept at all. */
    STANDARD;

    /**
     * @param asks      whether the save's {@link RuinsForkSetting} is on
     * @param supported whether this is a ruins site Marine Operations can fight on: an uncolonized world with a
     *                  ruins condition, not a gas giant. Anything else always gets vanilla's salvage team
     */
    public static RuinsRoute choose(boolean asks, boolean supported) {
        return asks && supported ? FORK : STANDARD;
    }

    /**
     * Whether a site is one an expedition can fight on.
     *
     * @param grade               the site's ruins grade, null when it has none
     * @param gasGiant            gas-giant ruins are sinking buoyant platforms, with no ground to land on
     * @param conditionMarketOnly an uncolonized world; a colony's ruins are its own to work
     */
    public static boolean supported(RuinsGrade grade, boolean gasGiant, boolean conditionMarketOnly) {
        return grade != null && !gasGiant && conditionMarketOnly;
    }
}
