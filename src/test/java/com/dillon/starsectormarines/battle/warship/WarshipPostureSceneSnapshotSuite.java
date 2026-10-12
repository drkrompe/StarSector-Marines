package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The warship posture loops' frames, from the same run that produced their verdicts. */
public final class WarshipPostureSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 720;

    public WarshipPostureSceneSnapshotSuite() {
        super(new WarshipPostureScene(), WIDTH, HEIGHT);
    }
}
