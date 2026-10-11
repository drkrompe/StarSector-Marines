package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The warship point-defence scene's frames, from the same run that produced its verdicts. */
public final class WarshipPointDefenceSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 720;

    public WarshipPointDefenceSceneSnapshotSuite() {
        super(new WarshipPointDefenceScene(), WIDTH, HEIGHT);
    }
}
