package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * {@link MechDuelRangeScene} recorded at 1920x1080, 100% scale: both pairings with the duel-range
 * rule on and off, from the same plays the verdicts are read from, whole map and a close view of the
 * fighting ground. The caption carries the range,
 * each side's rounds and its remaining integrity.
 */
public final class MechDuelRangeSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public MechDuelRangeSceneSnapshotSuite() {
        super(new MechDuelRangeScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override
    protected float[] closeView(String loopId) {
        // The ground the fights happen on, between the attacker's stop and the defender's leash.
        return new float[] {74f, 20.5f, 56f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override
    protected int[] stillFrames(String loopId) {
        // Frames every 15 ticks; the first round falls near tick 950-1150. First rounds, mid-fight, late.
        return new int[] {80, 110, 145};
    }
}
