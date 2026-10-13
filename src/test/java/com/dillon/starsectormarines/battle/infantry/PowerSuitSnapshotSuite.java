package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The exchange evidence at the project's review resolution and default UI scale. */
public final class PowerSuitSnapshotSuite extends BehaviorSceneSnapshotSuite {
    public PowerSuitSnapshotSuite() { super(new PowerSuitScene(), 1920, 1080); }
    @Override protected boolean showImpactEffects() { return true; }
    @Override protected int[] stillFrames(String loopId) { return new int[]{0, 5}; }
}
