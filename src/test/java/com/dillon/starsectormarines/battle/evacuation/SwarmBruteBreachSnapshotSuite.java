package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The Brute breach loops, from the same run that produced their verdicts. */
public final class SwarmBruteBreachSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public SwarmBruteBreachSnapshotSuite() {
        super(new SwarmBruteBreachScene(), 1920, 1080);
    }

    @Override protected boolean colourAnimation() { return true; }

    @Override protected int[] stillFrames(String loopId) {
        return loopId.equals("far-door") ? new int[]{6, 10, 13, 16} : new int[0];
    }
}
