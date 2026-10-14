package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The fort anti-air loops' frames (1920x1080), from the same run that produced their verdicts. */
public final class FortAirDefenceSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public FortAirDefenceSceneSnapshotSuite() {
        super(new FortAirDefenceScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected float[] closeView(String loopId) {
        return new float[]{FortAirDefenceScene.CX, FortAirDefenceScene.CY, 90f};
    }
}
