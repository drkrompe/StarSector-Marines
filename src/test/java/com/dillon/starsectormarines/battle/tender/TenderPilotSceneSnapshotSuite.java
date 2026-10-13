package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The piloted tender's frames, from the same run that produced its verdicts. */
public final class TenderPilotSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;

    public TenderPilotSceneSnapshotSuite() {
        super(new TenderPilotScene(), WIDTH, HEIGHT);
    }

    /** The hull hovers well above the ground, and its shadow lying clear of it is what says so. */
    @Override protected boolean showUnitShadows() { return true; }
}
