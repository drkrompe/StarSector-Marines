package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The fighter strafe and cover loops, from the same run that produced their verdicts. */
public final class FighterCoverSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public FighterCoverSceneSnapshotSuite() {
        super(new FighterCoverScene(), WIDTH, HEIGHT);
    }

    /** The aircraft is in the air, and its shadow lying clear of it is what says so. */
    @Override protected boolean showUnitShadows() { return true; }

    /** Cannon fire and bursts are the finding. */
    @Override protected boolean showImpactEffects() { return true; }

    /** A close recording over the contested ground, where the pass and the chase are legible. */
    @Override protected float[] closeView(String loopId) {
        if (loopId.startsWith("nest-") || loopId.equals("strafe-open")) return new float[]{72.5f, 35.5f, 56f};
        if (loopId.startsWith("cap-")) return new float[]{45.5f, 35.5f, 56f};
        return null;
    }

    /** Moments of a run in progress. */
    @Override protected int[] stillFrames(String loopId) {
        return new int[]{20, 40};
    }
}
