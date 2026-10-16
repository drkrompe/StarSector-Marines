package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The fleet-action loops (1920x1080, whole ground), from the same run that produced their verdicts and readings. */
public final class WarshipTacticsSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public WarshipTacticsSceneSnapshotSuite() {
        super(new WarshipTacticsScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }
}
