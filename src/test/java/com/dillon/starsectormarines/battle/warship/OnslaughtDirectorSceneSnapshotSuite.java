package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The directed Onslaught's frames, from the same run that produced its verdicts. */
public final class OnslaughtDirectorSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 810;

    public OnslaughtDirectorSceneSnapshotSuite() {
        super(new OnslaughtDirectorScene(), WIDTH, HEIGHT);
    }

    /** The hull hovers well above the ground, and its shadow lying clear of it is what says so. */
    @Override protected boolean showUnitShadows() { return true; }
}
