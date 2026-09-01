package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * {@link FiringLineScene} recorded: the file in the corridor, the rounds going
 * down it, and — in one loop only — marines shuffling a cell out of each
 * other's way between bursts.
 *
 * <p><b>The picture is what makes the two loops legible side by side.</b> The
 * numbers say how much of the squad's fire went into its own men; the animation
 * says why, because a column of six standing exactly on one another's lanes is
 * a shape you recognise at a glance and cannot read out of a damage total. The
 * move itself is a single cell, so the verdict lines remain the measurement —
 * a fifteen-tick cadence will not always catch a marine mid-step.
 *
 * <p>The arena is two cells wide for every one it is tall, and the frame is
 * sized to match rather than letterboxing a corridor into a square.
 */
public final class FiringLineSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 480;

    public FiringLineSceneSnapshotSuite() {
        super(new FiringLineScene(), WIDTH, HEIGHT);
    }
}
