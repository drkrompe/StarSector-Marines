package com.dillon.starsectormarines.battle.combat;

/**
 * How a point round's angular error disk is sized and sampled. Both profiles
 * keep the disk calibrated at weapon range and keep authored spread at full
 * weight; they differ only in how much the abstract accuracy figure widens
 * the disk and in where inside it a round tends to land.
 */
public enum PointAimDispersion {

    /**
     * Automated point fire: wall-breaching rounds, vehicle turrets, mech mounts.
     * Accuracy widens the disk at full weight and rounds land uniformly inside it.
     */
    WEAPON(2f, false),

    /**
     * A player's own cursor on a controlled marine. Accuracy was tuned as an
     * autonomous hit chance, and translated at full weight it buried every
     * weapon's character under the same wide disk, so it counts for about a
     * third here; rounds also cluster toward the cursor with an occasional
     * flier, so a click reads as where the shot went.
     */
    CONTROLLED(0.7f, true);

    /** Additional error at weapon range, in cells, for an accuracy of zero. */
    final float accuracyErrorCells;
    /** Whether rounds cluster toward the aim point rather than fill the disk evenly. */
    final boolean centreWeighted;

    PointAimDispersion(float accuracyErrorCells, boolean centreWeighted) {
        this.accuracyErrorCells = accuracyErrorCells;
        this.centreWeighted = centreWeighted;
    }
}
