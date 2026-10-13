package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The Close Air Support loops, from the same run that produced their verdicts. */
public final class CloseAirSupportSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public CloseAirSupportSceneSnapshotSuite() {
        super(new CloseAirSupportScene(), WIDTH, HEIGHT);
    }

    /** The hull is in the air, and its shadow lying clear of it is what says so. */
    @Override protected boolean showUnitShadows() { return true; }

    /** Rounds leaving the side of the hull are the finding; the shot layer and impacts show them. */
    @Override protected boolean showImpactEffects() { return true; }

    /** Key moments of both views: coming in, a flank to the ground and firing, and the far side of the ring. */
    @Override protected int[] stillFrames(String loopId) {
        if (loopId.equals("called")) return new int[]{8, 14, 30};
        return loopId.equals("called-close") ? new int[]{3, 12, 24, 36, 52} : new int[0];
    }

    /** A close recording around the target, where the broadside is legible. */
    @Override protected float[] closeView(String loopId) {
        return loopId.equals("called") ? new float[]{44.5f, 24.5f, 34f} : null;
    }
}
