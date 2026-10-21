package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The SwarmSpitter loops, from the same run that produced their verdicts. */
public final class SwarmSpitterSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public SwarmSpitterSceneSnapshotSuite() {
        super(new SwarmSpitterScene(), 1920, 1080);
    }
}
