package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.Client;
import com.dillon.starsectormarines.ops.intel.PlanetIntel;
import com.fs.starfarer.api.campaign.PlanetAPI;

/**
 * Context bundle handed to {@link StoryMissionDef} predicates and factories. Lets a
 * def read everything it might need to gate its appearance — current planet, current
 * client (faction + rep), planet intel snapshot, player's roster — without each def
 * needing to look these up itself.
 *
 * <p>{@link #seed} is supplied so build() can produce deterministic positions and
 * names without each def carrying its own RNG plumbing. Same seed → same mission;
 * driven by the generator's per-(planet, client) hash so revisits are stable.
 */
public final class StoryEligibilityContext {

    public final PlanetAPI    planet;
    public final Client       client;
    public final PlanetIntel  intel;
    public final MarineRoster roster;
    public final long         seed;

    public StoryEligibilityContext(PlanetAPI planet, Client client, PlanetIntel intel,
                                   MarineRoster roster, long seed) {
        this.planet = planet;
        this.client = client;
        this.intel  = intel;
        this.roster = roster;
        this.seed   = seed;
    }
}
