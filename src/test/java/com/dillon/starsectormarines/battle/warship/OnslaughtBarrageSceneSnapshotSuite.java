package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The Onslaught barrage's frames, from the same run that produced its verdicts. */
public final class OnslaughtBarrageSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 720;

    public OnslaughtBarrageSceneSnapshotSuite() {
        super(new OnslaughtBarrageScene(), WIDTH, HEIGHT);
    }

    /** The hull hovers well above the ground, and its shadow lying clear of it is what says so. */
    @Override protected boolean showUnitShadows() { return true; }

    /** The barrage is its landings, so the frames carry the game's own impact particles. */
    @Override protected boolean showImpactEffects() { return true; }

    /** The ship's flank and the walled compound it shells, at a scale where a blast and a hull can be compared. */
    @Override protected float[] closeView(String loopId) {
        return loopId.equals("barrage") ? new float[]{54f, 40f, 48f} : null;
    }
}
