package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The GroundColony loops, framed on the ground between the bore and the gun the colony answers. */
public final class GroundColonySceneSnapshotSuite extends BehaviorSceneSnapshotSuite {
    public GroundColonySceneSnapshotSuite() { super(new GroundColonyScene(), 1920, 1080); }
    @Override protected boolean showUnitShadows() { return true; }
    @Override protected boolean showImpactEffects() { return true; }
    @Override protected float closeViewScale() { return 1f; }
    @Override protected float[] closeView(String loop) {
        return new float[]{GroundColonyScene.GUN_X - 8f, GroundColonyScene.GUN_Y, 40f};
    }
    @Override protected int[] stillFrames(String loop) { return new int[]{10, 20, 30, 40, 47}; }
}
