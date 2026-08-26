package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * What a hull is <em>for</em>, as distinct from how big it is.
 *
 * <p>{@link HullClass} says how much ship there is; role says what the ship
 * does, and the two are close to independent. A Valkyrie and a Hammerhead are
 * both destroyer-sized and share almost no interior: one is a hold full of
 * bunks with the boat bays to put them ashore, the other is a magazine with a
 * crew wrapped around it. Sizing a deck from tonnage alone gave every hull the
 * same rooms and left a troop transport generating as a generic warship.
 *
 * <p>Roles come from the game's own {@code designation} column, which is
 * authored per hull and already says exactly this. Anything unrecognised is a
 * {@link #WARSHIP}, since that is what most hulls are and it is the reading that
 * degrades most gracefully.
 */
public enum HullRole {

    /** Fights. Crew is the complement, and the armory serves that crew. */
    WARSHIP,
    /** Carries a ground force and the means to land it. The mech bay and the boat bays both belong here. */
    TROOP_TRANSPORT,
    /** Operates small craft as its reason for existing. */
    CARRIER,
    /** Hauls cargo. Most of the hull is hold, and there is little else. */
    FREIGHTER,
    /** Hauls fuel. Like a freighter, and no more habitable for it. */
    TANKER,
    /** Carries passengers rather than troops: many berths, no armory worth the name. */
    LINER;

    /**
     * Read the role out of a hull's {@code designation}. Matching is on
     * substrings because the column is prose written per hull — "Light
     * Carrier", "Super Tanker", "Fast Freighter" — rather than a closed set.
     */
    public static HullRole fromDesignation(String designation) {
        if (designation == null) return WARSHIP;
        String text = designation.toLowerCase();
        if (text.contains("troop")) return TROOP_TRANSPORT;
        if (text.contains("carrier")) return CARRIER;
        if (text.contains("tanker")) return TANKER;
        if (text.contains("freighter") || text.contains("bulk")) return FREIGHTER;
        if (text.contains("liner") || text.contains("passenger")
                || text.contains("civilian transport")) {
            return LINER;
        }
        return WARSHIP;
    }

    /** Whether this hull services walkers and other heavy ground assets aboard. */
    public boolean landsGroundForces() {
        return this == TROOP_TRANSPORT;
    }
}
