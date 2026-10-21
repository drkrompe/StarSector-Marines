package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The SwarmDamageState loops close on the bore at 64 px per cell: whole, wounded, and the remains. */
public final class SwarmDamageStateSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    /** Thirty cells across a 1920-wide frame: 64 px per cell. */
    private static final float CELLS_ACROSS = 30f;

    public SwarmDamageStateSceneSnapshotSuite() {
        super(new SwarmDamageStateScene(), 1920, 1080);
    }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected float closeViewScale() { return 1f; }

    @Override
    protected float[] closeView(String loopId) {
        return new float[]{SwarmDamageStateScene.CENTRE + 0.5f, SwarmDamageStateScene.CENTRE + 0.5f, CELLS_ACROSS};
    }

    @Override
    protected int[] stillFrames(String loopId) {
        // One frame in each phase: whole, wounded, a colony dead, the bore dead.
        return new int[]{1, 3, 6, 10};
    }
}
