package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The CreepOutpost loops, whole map: the creep spreading and the outposts rooting on it. */
public final class CreepOutpostSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public CreepOutpostSceneSnapshotSuite() {
        super(new CreepOutpostScene(), 1920, 1080);
    }
}
