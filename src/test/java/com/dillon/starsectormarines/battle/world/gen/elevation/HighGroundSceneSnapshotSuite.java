package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The high-ground scene at play zoom over the overlook, with the ground's sun shading and shots drawn. */
public final class HighGroundSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {
    public HighGroundSceneSnapshotSuite() { super(new HighGroundScene(), 1920, 1080); }
    @Override protected boolean showUnitShadows() { return true; }
    @Override protected boolean showImpactEffects() { return true; }
    @Override protected float closeViewScale() { return 1f; }
    @Override protected float[] closeView(String loop) { return new float[]{32f, 18f, 64f}; }
    @Override protected int[] stillFrames(String loop) { return new int[]{4, 10, 16, 22, 40}; }
}
