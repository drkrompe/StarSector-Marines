package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The shield fort loops' frames, from the same run that produced their verdicts. */
public final class ShieldFortSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public ShieldFortSceneSnapshotSuite() {
        super(new ShieldFortScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }
}
