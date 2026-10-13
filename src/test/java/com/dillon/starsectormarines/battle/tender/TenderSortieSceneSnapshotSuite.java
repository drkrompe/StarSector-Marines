package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The tender wing's three loops, from the same run that produced their verdicts. */
public final class TenderSortieSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;

    public TenderSortieSceneSnapshotSuite() {
        super(new TenderSortieScene(), WIDTH, HEIGHT);
    }

    /** The hull hovers well above the ground, and its shadow lying clear of it is what says so. */
    @Override protected boolean showUnitShadows() { return true; }

    /** Close on the hull, so the wing's ring and its launch and return lines read against it. */
    @Override protected float[] closeView(String loopId) {
        return new float[] {34f, 40f, 24f};
    }
}
