package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The escort loops' frames, from the same run that produced their verdicts. */
public final class ShipEscortSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 440;

    public ShipEscortSceneSnapshotSuite() {
        super(new ShipEscortScene(), WIDTH, HEIGHT);
    }

    /** The lead, its escort and the wing working them, closer than the whole map shows them. */
    @Override protected float[] closeView(String loopId) {
        return new float[]{100f, 28f, 70f};
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }
}
