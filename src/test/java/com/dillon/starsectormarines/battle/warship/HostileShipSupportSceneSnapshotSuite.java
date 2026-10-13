package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The hostile ship loops, from the same run that produced their verdicts. */
public final class HostileShipSupportSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 810;

    public HostileShipSupportSceneSnapshotSuite() {
        super(new HostileShipSupportScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showUnitShadows() { return true; }
}
