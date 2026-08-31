package com.dillon.starsectormarines.battle.world.gen;

/**
 * How a settlement is joined to the rest of its world — its lifeline for
 * supplies, relief and reinforcement.
 *
 * <p>This exists as a stated value rather than as a blanket law because not
 * every place on a map is a living settlement. An inhabited colony that nothing
 * can reach is a generation defect; a ruin nothing can reach is the point of the
 * ruin. Writing the requirement as "every map must have a link" would have made
 * the abandoned case an exception to a rule, which is how exceptions get
 * forgotten. Here it is {@link #NONE} — an ordinary value the generator can be
 * asked for.
 *
 * <p>The defect this guards against hides well. {@code RoadGraphBuilder}
 * promotes a perimeter cell to an off-map entry node only where a wide enough
 * band actually reaches the map edge, so a settlement with no road out has
 * nowhere for ground reinforcement to arrive from, and nothing reports it.
 */
public enum SettlementLink {

    /**
     * Joined to the planetary road network: at least one arterial runs off the
     * map edge. The ordinary case for a colony that grew where people could
     * drive to it.
     */
    ROAD,

    /**
     * Off-grid. No road leaves the map; supplies and relief arrive by ship, so
     * the settlement must instead hold a landing facility. A mining outpost on a
     * rock nobody paved a way to.
     */
    LANDING,

    /**
     * Nothing. Abandoned, ruined, or never connected — no road out and no
     * guarantee of a pad. Reaching it is the attacker's problem.
     */
    NONE,
}
