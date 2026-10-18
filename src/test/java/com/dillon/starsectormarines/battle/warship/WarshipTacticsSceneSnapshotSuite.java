package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The fleet-action loops (1920x1080, following the hulls), from the same run that produced their verdicts and readings. */
public final class WarshipTacticsSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public WarshipTacticsSceneSnapshotSuite() {
        super(new WarshipTacticsScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    /** Follows the hulls rather than framing the whole map; {@code -Dsnapshot.duelCamera=false} restores the map. */
    @Override protected boolean followWarships() { return true; }
}
