package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The CreepVegetation loop: the whole country, and the near grove's edge at play zoom as the creep takes it. */
public final class CreepVegetationSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public CreepVegetationSceneSnapshotSuite() {
        super(new CreepVegetationScene(), 1920, 1080);
    }

    @Override protected float[] closeView(String loopId) { return CreepVegetationScene.groveView(); }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected int[] stillFrames(String loopId) { return new int[]{4, 8, 12, 16, 22, 30, 41}; }
}
