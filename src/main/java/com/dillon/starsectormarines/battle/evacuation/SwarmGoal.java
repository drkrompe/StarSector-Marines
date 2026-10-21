package com.dillon.starsectormarines.battle.evacuation;

/**
 * A place the swarm is drawn toward (`colony-breach-nouns.md`, Swarm goal).
 * Each goal has one shared flow field over the battle's navigation grid; see
 * {@link SwarmMovement}.
 */
public enum SwarmGoal {
    /**
     * The nearest prey body the swarm may know: every combatant the swarm is
     * hostile to, gathered each tick, less the shelter guard while the shelter
     * is sealed. Civilians are absent: the cohort is found by contact, not known.
     */
    PREY,
    /** The rescue shelter. Disclosed, so its cells are declared by the mission. */
    SHELTER,
    /** The lift zone. Disclosed, so its cells are declared by the mission. */
    LIFT,
    /**
     * The nearest living bore, for bodies carrying biomass home. The bores
     * are the swarm's own, so their cells are declared, and redeclared as a
     * bore opens or collapses.
     */
    BORE
}
