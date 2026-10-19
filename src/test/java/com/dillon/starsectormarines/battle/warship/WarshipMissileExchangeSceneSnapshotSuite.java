package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The missile exchange loops (1920x1080, through the duel camera), from the same run that produced their verdicts. */
public final class WarshipMissileExchangeSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public WarshipMissileExchangeSceneSnapshotSuite() {
        super(new WarshipMissileExchangeScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    /** Follows the hulls rather than framing the whole map; {@code -Dsnapshot.duelCamera=false} restores the map. */
    @Override protected boolean followWarships() { return true; }
}
