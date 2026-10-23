package com.dillon.starsectormarines.battle.objective.optional;

import java.util.Map;

/**
 * What an optional objective pays when it succeeds.
 *
 * @param credits paid to the player
 * @param materials commodity id to quantity, paid as cargo after the battle
 * @param grants keyed things the campaign arc interprets (standing, say); recorded on the outcome, never applied here
 */
public record Reward(int credits, Map<String, Integer> materials, Map<String, Integer> grants) {

    public static final Reward NONE = new Reward(0, Map.of(), Map.of());

    public Reward {
        credits = Math.max(0, credits);
        materials = materials == null ? Map.of() : Map.copyOf(materials);
        grants = grants == null ? Map.of() : Map.copyOf(grants);
    }

    public boolean isEmpty() { return credits == 0 && materials.isEmpty() && grants.isEmpty(); }
}
