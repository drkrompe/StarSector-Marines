package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * Somewhere on a map that holds work: a ship's compartment, a building interior,
 * a walled yard, anything a generator gave a purpose and an extent.
 *
 * <p>The ambient model needs exactly three things from a place and this is all
 * of them. Its <b>extent</b> says which of the map's authored jobs are here,
 * its <b>purpose</b> says whose jobs those are, and its <b>id</b> scopes the
 * claim groups so somebody looking for a free bench is offered one in the room
 * they are standing in rather than the nearest one three compartments away.
 *
 * <p>Deliberately not a compartment. Everything downstream of a fixture — what
 * it affords, whose job that is, the loop somebody walks between them — is the
 * same problem in a berthing space and in a market square, and the only reason
 * it began life tied to a ship is that a ship is where it was needed first.
 * Tying it to one map family would mean writing it a second time for civilians,
 * and a second implementation of "who has business here" is how two parts of a
 * game come to disagree about it.
 */
public interface JobSite {

    /** Scopes this site's claim groups. Unique within one map. */
    int id();

    /** What this place is for, which is what decides whose jobs it holds. */
    RoomPurpose purpose();

    /** Whether this site covers the given map cell. */
    boolean contains(int cellX, int cellY);

    /**
     * Roughly the middle of this site, for judging which of two places offering
     * the same job is the nearer one to walk to.
     *
     * <p>Approximate on purpose. A shift picks the nearest mess once, when it
     * is built, and the difference between a room's centre and the particular
     * seat somebody ends up claiming is smaller than the difference between two
     * candidate rooms. Anything more exact would mean pathfinding every
     * compartment against every other one to answer a question about which end
     * of the ship a thing is at.
     */
    int centreX();

    /** @see #centreX() */
    int centreY();
}
