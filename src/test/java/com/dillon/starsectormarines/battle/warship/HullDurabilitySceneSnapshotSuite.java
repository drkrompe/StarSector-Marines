package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The class-matchup duels (1920x1080, whole ground), from the same run that produced their verdicts and readings. */
public final class HullDurabilitySceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public HullDurabilitySceneSnapshotSuite() {
        super(new HullDurabilityScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }
}
