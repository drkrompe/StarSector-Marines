package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The ships-against-forts loops' frames (1920x1080), from the same run that produced their readings. */
public final class ShipVsFortSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public ShipVsFortSceneSnapshotSuite() {
        super(new ShipVsFortScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected float[] closeView(String loopId) {
        return new float[]{FortAirDefenceScene.CX - 14f, FortAirDefenceScene.CY, 70f};
    }
}
