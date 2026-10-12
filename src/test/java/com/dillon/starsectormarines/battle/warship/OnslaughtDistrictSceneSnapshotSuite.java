package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The district scene's frames, from the same runs that produced its verdicts. */
public final class OnslaughtDistrictSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 810;

    public OnslaughtDistrictSceneSnapshotSuite() {
        super(new OnslaughtDistrictScene(), WIDTH, HEIGHT);
    }
}
