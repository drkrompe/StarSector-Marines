package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The Onslaught barrage's frames, from the same run that produced its verdicts. */
public final class OnslaughtBarrageSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 720;

    public OnslaughtBarrageSceneSnapshotSuite() {
        super(new OnslaughtBarrageScene(), WIDTH, HEIGHT);
    }
}
