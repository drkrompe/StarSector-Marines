package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The warship missiles scene's frames, from the same run that produced its verdicts. */
public final class WarshipMissilesSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public WarshipMissilesSceneSnapshotSuite() {
        super(new WarshipMissilesScene(), WIDTH, HEIGHT);
    }
}
