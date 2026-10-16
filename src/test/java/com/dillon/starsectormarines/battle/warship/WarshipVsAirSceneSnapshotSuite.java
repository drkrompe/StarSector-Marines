package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The ship-versus-air loops' frames (1920x1080), from the same run that produced their verdicts. */
public final class WarshipVsAirSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public WarshipVsAirSceneSnapshotSuite() {
        super(new WarshipVsAirScene(), 1920, 1080);
    }

    /** The ship, its guns' reach and the craft crossing it, closer than the whole map shows them. */
    @Override protected float[] closeView(String loopId) {
        return loopId.equals("control-transport") || loopId.equals("control-wing") ? null
                : new float[]{WarshipVsAirScene.SHIP_X, WarshipVsAirScene.SHIP_Y, 80f};
    }

    /** The close view is shown to people, so it is recorded at the full 1920x1080 too. */
    @Override protected float closeViewScale() { return 1f; }

    /** Tracers, hits and the wrecks are the evidence. */
    @Override protected boolean showImpactEffects() { return true; }

    /** Hovering craft and the hull overhead read by their shadows. */
    @Override protected boolean showUnitShadows() { return true; }
}
