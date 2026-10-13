package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The commander-called mech drop loops' frames at 1920x1080, from the same run that produced their verdicts. */
public final class MechDropSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public MechDropSceneSnapshotSuite() {
        super(new MechDropScene(), WIDTH, HEIGHT);
    }
}
