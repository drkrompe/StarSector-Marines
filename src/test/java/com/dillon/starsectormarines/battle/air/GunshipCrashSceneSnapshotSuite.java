package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

public final class GunshipCrashSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {
    public GunshipCrashSceneSnapshotSuite() { super(new GunshipCrashScene(), 1920, 1080); }
    @Override protected boolean showUnitShadows() { return true; }
    @Override protected boolean showImpactEffects() { return true; }
    @Override protected float closeViewScale() { return 1f; }
    @Override protected float[] closeView(String loop) { return loop.equals("impact") ? new float[]{40.5f, 27.5f, 28f} : null; }
    @Override protected int[] stillFrames(String loop) { return loop.equals("impact-close") ? new int[]{20, 26, 34, 60, 90} : new int[0]; }
}
