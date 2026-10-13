package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The air-strike loops' frames, from the same run that produced their verdicts. */
public final class WarshipAirStrikeSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 440;

    public WarshipAirStrikeSceneSnapshotSuite() {
        super(new WarshipAirStrikeScene(), WIDTH, HEIGHT);
    }

    /** The hull and the wing working it, closer than the whole map shows them. */
    @Override protected float[] closeView(String loopId) {
        return loopId.equals("control-no-strike") ? null : new float[]{110f, 32f, 70f};
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }
}
