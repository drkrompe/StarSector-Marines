package com.dillon.starsectormarines.battle.shield;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The shield-dome loops' frames at 1920x1080, from the same run that produced their verdicts. */
public final class ShieldDomeSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public ShieldDomeSceneSnapshotSuite() {
        super(new ShieldDomeScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() {
        return true;
    }

    @Override protected int[] stillFrames(String loopId) {
        return loopId.equals("dome") ? new int[]{10, 14, 22, 30, 44} : new int[0];
    }

    @Override protected float[] closeView(String loopId) {
        return loopId.equals("dome") ? new float[]{90f, 46f, 48f} : null;
    }
}
