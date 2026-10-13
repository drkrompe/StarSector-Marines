package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The battery fort loops' frames, from the same run that produced their verdicts. */
public final class BatteryFortSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public BatteryFortSceneSnapshotSuite() {
        super(new BatteryFortScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }
}
