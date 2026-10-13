package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The missile tactics scene's frames, from the same run that produced its verdicts. */
public final class WarshipMissileTacticsSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    public WarshipMissileTacticsSceneSnapshotSuite() {
        super(new WarshipMissileTacticsScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    /** The flank and the beam are mid-map and small against it, so every loop is also recorded close on the ground. */
    @Override protected float[] closeView(String loopId) {
        if (loopId.equals("hurricane-control")) return null;
        if (loopId.startsWith("dragonfire")) return new float[]{76f, 45f, 52f};
        if (loopId.equals("hurricane-close")) return new float[]{48f, 45f, 52f};
        return new float[]{70f, 45f, 70f};
    }

    @Override protected boolean showUnitShadows() { return true; }
}
