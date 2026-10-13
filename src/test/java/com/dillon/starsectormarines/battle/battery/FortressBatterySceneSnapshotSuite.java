package com.dillon.starsectormarines.battle.battery;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The battery loops' frames, from the same run that produced their verdicts. */
public final class FortressBatterySceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 440;

    public FortressBatterySceneSnapshotSuite() {
        super(new FortressBatteryScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }
}
