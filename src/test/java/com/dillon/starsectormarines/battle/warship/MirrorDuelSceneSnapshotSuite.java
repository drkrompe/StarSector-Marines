package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * The equal-hull duels (1920x1080, whole ground), from the same run that produced their verdicts and readings.
 * {@code -Dbattle.scene.warshipMirrors.only=eagle-37,wolf} records only the named loops or classes.
 */
public final class MirrorDuelSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public MirrorDuelSceneSnapshotSuite() {
        super(new MirrorDuelScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }
}
