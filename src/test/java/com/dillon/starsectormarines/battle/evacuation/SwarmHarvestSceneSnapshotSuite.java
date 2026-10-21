package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The SwarmHarvest loops, from the same run that produced their verdicts. */
public final class SwarmHarvestSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public SwarmHarvestSceneSnapshotSuite() {
        super(new SwarmHarvestScene(), 1920, 1080);
    }
}
