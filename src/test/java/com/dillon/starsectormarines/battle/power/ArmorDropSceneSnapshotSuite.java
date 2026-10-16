package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The armor-drop loop's frames at 1920x1080, from the same run that produced its verdicts. */
public final class ArmorDropSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public ArmorDropSceneSnapshotSuite() {
        super(new ArmorDropScene(), WIDTH, HEIGHT);
    }

    /**
     * Frames are every 30 ticks: the Hound inbound, the Hound down beside the held call, the APC inbound, and the APC
     * down with the Buffalo gone (ticks 240, 600, 1560, 1980).
     */
    @Override
    protected int[] stillFrames(String loopId) {
        return new int[]{8, 20, 52, 66};
    }
}
