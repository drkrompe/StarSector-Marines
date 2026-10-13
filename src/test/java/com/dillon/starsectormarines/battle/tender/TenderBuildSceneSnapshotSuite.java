package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The tender's four loops, from the same run that produced their verdicts. */
public final class TenderBuildSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;

    public TenderBuildSceneSnapshotSuite() {
        super(new TenderBuildScene(), WIDTH, HEIGHT);
    }

    /** The hull hovers well above the ground, and its shadow lying clear of it is what says so. */
    @Override protected boolean showUnitShadows() { return true; }

    /** Where the tender stands off from the site, close enough to read the drones' own sprites beside the hull. */
    @Override protected float[] closeView(String loopId) {
        return loopId.equals("build") || loopId.equals("raid") ? new float[] {57.5f, 40.5f, 22f} : null;
    }
}
