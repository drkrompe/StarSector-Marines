package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.air.ShuttleType;

/**
 * What a hull keeps in her bays.
 *
 * <p><b>A ship's boats are a fitting, not property.</b> They are hers the way
 * her boat bay is hers: you do not bring them aboard and you cannot take them
 * with you, and a company that changes ships changes boats. That is the whole
 * reason this is derived from the hull rather than looked up against anybody's
 * roster — the ship the company is standing in is the ship, and what is in her
 * bays follows from what she is, exactly as her room program does.
 *
 * <p><b>A boat is small.</b> {@link ShuttleType} spans both ends of a range it
 * once had to: a Valkyrie in that list is a transport that flies a detachment
 * down from a fleet, and under the older framing that was what a lift was made
 * of. It is not what a bay holds. A Valkyrie is a ship you are aboard, and the
 * things inside her are her boats — so what belongs here is the small end of
 * that list, and a bay stocked with another Valkyrie would be a ship carrying
 * herself.
 *
 * <p>What decides which small craft is what the hull is <em>for</em>. A hull
 * whose reason for existing is putting a ground force somewhere carries landing
 * craft; every other hull carries a gig, which is ship's business — a run to
 * another hull, a party ashore, an errand. The distinction is real and the game
 * already cares about it, which is why it is read off {@link HullRole} rather
 * than invented here.
 */
public final class ShipsBoats {

    /**
     * The lander: what a bay holds on a hull that exists to put people on the
     * ground. Nimble, one fire team, and the craft the marine lift is flown in.
     */
    private static final ShuttleType LANDING_CRAFT = ShuttleType.AEROSHUTTLE;

    /**
     * The gig: a hull's own boat, for a hull with no ground force to land.
     * A courier rather than an assault craft — the same size, a different job.
     */
    private static final ShuttleType GIG = ShuttleType.HERMES;

    private ShipsBoats() { }

    /**
     * What this hull's bays are stocked with.
     *
     * <p>One type per hull rather than a mixed bill. A ship's boats are a class
     * of boat she carries several of, not a collection assembled from wherever;
     * a bay holding one of each would be a fleet in miniature, which is the
     * framing this exists to replace.
     */
    public static ShuttleType carriedBy(HullRole role) {
        if (role == null) return GIG;
        return switch (role) {
            case TROOP_TRANSPORT, CARRIER -> LANDING_CRAFT;
            case WARSHIP, FREIGHTER, TANKER, LINER -> GIG;
        };
    }
}
