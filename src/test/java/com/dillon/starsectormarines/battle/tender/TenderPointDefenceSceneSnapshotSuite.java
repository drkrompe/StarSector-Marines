package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The tender point-defence scene's loops, from the same run that produced their verdicts. */
public final class TenderPointDefenceSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;

    public TenderPointDefenceSceneSnapshotSuite() {
        super(new TenderPointDefenceScene(), WIDTH, HEIGHT);
    }

    /** The hull hovers above the ground, and its shadow lying clear of it is what says so. */
    @Override protected boolean showUnitShadows() { return true; }

    /** Close on the tender, where the wing's beams and the incoming rounds can be told apart. */
    @Override protected float[] closeView(String loopId) {
        return new float[] {88f, 40f, 30f};
    }
}
