package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The screen loop (1920x1080): an escort on the line catching rounds meant for its charge, from the verdicts' run. */
public final class WarshipScreenSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public WarshipScreenSceneSnapshotSuite() {
        super(new WarshipScreenScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    /** The close view is shown to people, so it is recorded at the full 1920x1080 too. */
    @Override protected float closeViewScale() { return 1f; }

    /** The line between the shooter and the charge, with the escort on it. */
    @Override protected float[] closeView(String loopId) {
        return WarshipScreenScene.screenView;
    }
}
