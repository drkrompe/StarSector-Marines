package com.dillon.starsectormarines.battle.warship;

/**
 * The flanking approach of a submunition whose spec calls for one (the Hydra's platforms, whose
 * {@code angledApproachDist} is the distance abeam of the target they come in from).
 *
 * <p>On the ground there is no shield to flank, so the manoeuvre is read as its picture: the platforms
 * leave the split at right angles to the line of fire, alternate ones to either side, swing out
 * abeam of the ground by about half the spec's approach distance (never more than the leg allows),
 * and then close on their aim points from the side. The path carries it; this decides the bearing
 * it leaves on and how far out it bulges.
 */
final class BloomFlank {

    /** How far abeam a platform swings, as a share of its spec's angled-approach distance. */
    static final float ABEAM_SHARE = 0.5f;
    /** The bulge is never less than this share of the leg, so a short leg still visibly swings. */
    static final float MIN_LOOP = 0.15f;
    /** Nor more than this, so the arrival is an angle rather than a U-turn. */
    static final float MAX_LOOP = 0.7f;

    private BloomFlank() {}

    /** The bearing, in radians, the {@code index}th warhead leaves on: forward turned a quarter to alternating sides. */
    static float outward(float forwardRadians, int index) {
        float quarter = (float) (Math.PI / 2.0);
        return forwardRadians + ((index & 1) == 0 ? quarter : -quarter);
    }

    /** The {@link BloomPath} loop for a leg of {@code chordCells} whose spec approaches from {@code approachCells} abeam. */
    static float loop(float approachCells, float chordCells) {
        if (chordCells <= 1e-3f) return MIN_LOOP;
        float loop = approachCells * ABEAM_SHARE / chordCells;
        return Math.max(MIN_LOOP, Math.min(MAX_LOOP, loop));
    }
}
