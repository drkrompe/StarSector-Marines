package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The lance sweep's frames, from the same run that produced its verdicts. */
public final class WarshipLanceSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 960;

    public WarshipLanceSceneSnapshotSuite() {
        super(new WarshipLanceScene(), WIDTH, HEIGHT);
    }
}
