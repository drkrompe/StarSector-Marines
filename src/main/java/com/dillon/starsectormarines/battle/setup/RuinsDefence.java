package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;

/**
 * Who defends a ruins site, decided by its occupant alone ({@code ruins-expedition.md}, Occupants). A site's force is
 * its occupant's and nothing else: no generic garrison, patrols, emplacements or air ever join it.
 */
public enum RuinsDefence {
    /** A scavenger crew posted at its dig and its camp ({@code RuinsCrew}). */
    CREW,
    /** Derelict security's walkers under the security-network director ({@code DerelictSecurity}). */
    SECURITY,
    /** The Rav's closed bores under the swarm director ({@code RuinsSleepers}). */
    SLEEPERS,
    /** Nobody: the expedition is the site's works, and no one opposes it. */
    NONE;

    public static RuinsDefence of(RuinsOccupant occupant) {
        if (occupant == null) throw new IllegalArgumentException("a ruins site needs its occupant");
        return switch (occupant) {
            case SCAVENGERS -> CREW;
            case DOMAIN_SECURITY, CONTINGENCY_SECURITY -> SECURITY;
            case RAV -> SLEEPERS;
            case NOBODY -> NONE;
        };
    }
}
