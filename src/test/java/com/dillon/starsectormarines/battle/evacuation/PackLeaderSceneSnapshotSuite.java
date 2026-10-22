package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The pack-leader loop, from the same run that produced its verdicts. */
public final class PackLeaderSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public PackLeaderSceneSnapshotSuite() {
        super(new PackLeaderScene(), 1920, 1080);
    }
}
