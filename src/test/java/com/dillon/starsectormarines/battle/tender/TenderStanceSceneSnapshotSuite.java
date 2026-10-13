package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The tender stance loops, from the same run that produced their verdicts. */
public final class TenderStanceSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;

    public TenderStanceSceneSnapshotSuite() {
        super(new TenderStanceScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showUnitShadows() { return true; }
}
