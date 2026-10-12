package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The ship-versus-air loops' frames, from the same run that produced their verdicts. */
public final class WarshipVsAirSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 440;

    public WarshipVsAirSceneSnapshotSuite() {
        super(new WarshipVsAirScene(), WIDTH, HEIGHT);
    }

    /** The ship, its guns' reach and the craft crossing it, closer than the whole map shows them. */
    @Override protected float[] closeView(String loopId) {
        return loopId.equals("control-transport") || loopId.equals("control-wing") ? null : new float[]{120f, 32f, 80f};
    }

    /** Tracers, hits and the wrecks are the evidence. */
    @Override protected boolean showImpactEffects() { return true; }

    /** Hovering craft and the hull overhead read by their shadows. */
    @Override protected boolean showUnitShadows() { return true; }
}
