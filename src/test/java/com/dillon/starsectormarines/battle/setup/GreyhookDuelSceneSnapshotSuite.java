package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The Greyhook duel loops (1920x1080, 100% scale), from the same run that produced their verdicts and readings. */
public final class GreyhookDuelSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public GreyhookDuelSceneSnapshotSuite() {
        super(new GreyhookDuelScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    /** Follows the hulls rather than framing the whole map; {@code -Dsnapshot.duelCamera=false} restores the map. */
    @Override protected boolean followWarships() { return true; }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected float[] closeView(String loopId) {
        return null;
    }
}
