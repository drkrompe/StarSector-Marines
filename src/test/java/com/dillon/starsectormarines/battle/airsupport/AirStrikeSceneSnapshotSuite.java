package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The air-strike loops, from the same run that produced their verdicts. */
public final class AirStrikeSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public AirStrikeSceneSnapshotSuite() {
        super(new AirStrikeScene(), WIDTH, HEIGHT);
    }

    /** The aircraft is in the air, and its shadow lying clear of it is what says so. */
    @Override protected boolean showUnitShadows() { return true; }

    /** Bombs landing and the guns answering are the finding. */
    @Override protected boolean showImpactEffects() { return true; }

    /** A close recording round the garrison, where the pass and the guns are legible. */
    @Override protected float[] closeView(String loopId) {
        return loopId.equals("light-target") || loopId.equals("fort-aa-forced")
                ? new float[]{50.5f, 25.5f, 56f} : null;
    }

    /** Moments of a run in progress: the pass, the guns answering, the pool part-spent. */
    @Override protected int[] stillFrames(String loopId) {
        switch (loopId) {
            case "light-target": return new int[]{14, 20};
            case "fort-aa-forced": return new int[]{16, 22, 28};
            case "pool-drain": return new int[]{30, 60};
            default: return new int[0];
        }
    }
}
