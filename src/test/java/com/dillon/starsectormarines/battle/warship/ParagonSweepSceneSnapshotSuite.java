package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The Paragon's sweep frames, from the same run that produced its verdicts. */
public final class ParagonSweepSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 1080;

    public ParagonSweepSceneSnapshotSuite() {
        super(new ParagonSweepScene(), WIDTH, HEIGHT);
    }

    /** Wall dust and the fires a sweep leaves are part of what it does. */
    @Override protected boolean showImpactEffects() { return true; }
}
