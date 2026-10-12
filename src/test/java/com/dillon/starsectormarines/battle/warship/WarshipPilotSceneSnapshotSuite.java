package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The piloted Onslaught's frames, from the same run that produced its verdicts. */
public final class WarshipPilotSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 810;

    public WarshipPilotSceneSnapshotSuite() {
        super(new WarshipPilotScene(), WIDTH, HEIGHT);
    }

    /** The hull hovers well above the ground, and its shadow lying clear of it is what says so. */
    @Override protected boolean showUnitShadows() { return true; }
}
