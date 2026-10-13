package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The defence-structure command loops' frames at 1920x1080, from the same run that produced their verdicts. */
public final class DefenceStructureCommandSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public DefenceStructureCommandSceneSnapshotSuite() {
        super(new DefenceStructureCommandScene(), WIDTH, HEIGHT);
    }
}
