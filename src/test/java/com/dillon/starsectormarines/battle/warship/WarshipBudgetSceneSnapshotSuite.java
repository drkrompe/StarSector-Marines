package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The warship budget loops' frames, from the same run that produced their verdicts. */
public final class WarshipBudgetSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 720;

    public WarshipBudgetSceneSnapshotSuite() {
        super(new WarshipBudgetScene(), WIDTH, HEIGHT);
    }
}
