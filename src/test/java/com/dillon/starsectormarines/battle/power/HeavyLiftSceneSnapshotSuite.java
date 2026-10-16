package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The heavy-lift loops' frames at 1920x1080, from the same run that produced their verdicts. */
public final class HeavyLiftSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public HeavyLiftSceneSnapshotSuite() {
        super(new HeavyLiftScene(), WIDTH, HEIGHT);
    }

    /** Frames are every 15 ticks: the carrier inbound (tick 180) and the machine down (about tick 340). */
    @Override
    protected int[] stillFrames(String loopId) {
        return loopId.equals("bulwark-refused") ? new int[]{6} : new int[]{12, 24};
    }
}
