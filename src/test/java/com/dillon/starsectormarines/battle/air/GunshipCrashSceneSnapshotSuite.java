package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

public final class GunshipCrashSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {
    public GunshipCrashSceneSnapshotSuite() { super(new GunshipCrashScene(), 1920, 1080); }
    @Override protected boolean showUnitShadows() { return true; }
    @Override protected boolean showImpactEffects() { return true; }
    @Override protected float closeViewScale() { return 1f; }
    @Override protected float[] closeView(String loop) { return (loop.equals("momentum") || loop.equals("hover")) ? new float[]{46f, 27.5f, 64f} : null; }
    @Override protected int[] stillFrames(String loop) { return (loop.equals("momentum-close") || loop.equals("hover-close")) ? new int[]{62, 65, 68, 72, 85} : new int[0]; }
}
