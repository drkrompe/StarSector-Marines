package com.dillon.starsectormarines.battle.warship;

import java.util.function.DoubleUnaryOperator;

/**
 * Where along its line to the ground a multi-warhead missile's carrier splits.
 *
 * <p>The reading of the spec is vanilla's: the carrier splits once it is within
 * {@code splitRange} of the ground it was sent at, but its dispenser is not armed until
 * {@code minTimeToSplit} has run. A carrier whose spec says {@code canSplitEarly} is let split
 * the moment it is in range, however little it has flown, which is a Hurricane or a Sabot
 * fired at a close district. One that does not say so flies on past its split range until
 * the dispenser arms, and so splits nearer the ground. (Of the install's MIRVs the Hurricane
 * and the Sabot say it; the Hydra and the Pilum do not.)
 *
 * <p>Both ends are held to shares of the line so a warhead always has room to fan, and a
 * carrier that has to fly on is never made to split on top of the target.
 */
final class MirvSplit {

    /** A carrier splits no nearer than this share of its way to the ground, so a close target still gives its warheads room to fan. */
    static final float MIN_SHARE = 0.35f;
    /** A carrier that has to fly a minimum time before it may split is never made to split nearer the ground than this share. */
    static final float MAX_SHARE = 0.92f;
    private static final float PUSH_FACTOR = 1.3f;
    private static final float PUSH_BONUS_CELLS = 0.5f;
    private static final int PUSH_LIMIT = 6;

    private MirvSplit() {}

    /**
     * The distance from the muzzle, in cells, at which the carrier splits.
     *
     * @param distance        cells from the muzzle to the ground the missile was sent at
     * @param splitRangeCells the spec's split range, in cells
     * @param minTimeToSplit  seconds the dispenser takes to arm
     * @param canSplitEarly   whether the spec lets the carrier split before it has armed
     * @param secondsToCover  how long the carrier's own flight takes to reach a given distance
     */
    static float along(float distance, float splitRangeCells, float minTimeToSplit, boolean canSplitEarly,
                       DoubleUnaryOperator secondsToCover) {
        float along = Math.max(distance - splitRangeCells, MIN_SHARE * distance);
        if (canSplitEarly) return along;
        for (int guard = 0; guard < PUSH_LIMIT && secondsToCover.applyAsDouble(along) < minTimeToSplit
                && along < MAX_SHARE * distance; guard++) {
            along = Math.min(MAX_SHARE * distance, along * PUSH_FACTOR + PUSH_BONUS_CELLS);
        }
        return along;
    }
}
