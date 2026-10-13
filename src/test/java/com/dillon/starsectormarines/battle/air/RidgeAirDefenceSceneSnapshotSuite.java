package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

public final class RidgeAirDefenceSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {
    public RidgeAirDefenceSceneSnapshotSuite() { super(new RidgeAirDefenceScene(), 1920, 1080); }
    @Override protected boolean showUnitShadows() { return true; }
    @Override protected boolean showImpactEffects() { return true; }
    @Override protected boolean colourAnimation() { return true; }
    @Override protected float closeViewScale() { return 1f; }
    @Override protected float[] closeView(String loop) {
        return loop.equals("cleared-batteries") ? null : new float[]{180f, 45f, 240f};
    }
    @Override protected int[] stillFrames(String loop) { return new int[]{0, 75, 120}; }
}
