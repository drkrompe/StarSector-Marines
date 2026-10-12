package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The Onslaught flyby's frames, from the same run that produced its verdicts. */
public final class OnslaughtFlybySceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1560;
    private static final int HEIGHT = 600;

    public OnslaughtFlybySceneSnapshotSuite() {
        super(new OnslaughtFlybyScene(), WIDTH, HEIGHT);
    }
}
