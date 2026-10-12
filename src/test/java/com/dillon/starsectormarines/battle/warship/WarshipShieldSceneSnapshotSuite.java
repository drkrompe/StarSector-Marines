package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The warship shield loops' frames, from the same run that produced their verdicts. */
public final class WarshipShieldSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 720;

    public WarshipShieldSceneSnapshotSuite() {
        super(new WarshipShieldScene(), WIDTH, HEIGHT);
    }

    /** The blasts and the shot layer are what a shield is for; without them a hit is invisible. */
    @Override protected boolean showImpactEffects() {
        return true;
    }

    /** The bubble is eighteen cells across on an eighty-cell map; a close view is where the arc reads. */
    @Override protected float[] closeView(String loopId) {
        return loopId.equals("front-arc-held") || loopId.equals("auto-threat")
                ? new float[]{60f, 42f, 44f} : null;
    }
}
