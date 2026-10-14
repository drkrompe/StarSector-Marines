package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

import java.util.List;

/**
 * The planned assault's frames, from the same run that produced its verdicts: only the {@code plan} loop, in the window
 * where the company positions its base of fire, suppresses and enters, at 1920x1080 and 100% scale, whole map and a
 * close view of the fort's west face. Every close frame is also written as a PNG still so a moment can be read.
 */
public final class AssaultMusterSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public AssaultMusterSceneSnapshotSuite() {
        super(new AssaultMusterScene(List.of("plan"), 4200, 7500, 150), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    /** The fort's west face: the main gate, its two bastion nests and the ground in front of them. */
    @Override protected float[] closeView(String loopId) {
        return new float[] {118f, 208f, 110f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        int[] all = new int[40];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return all;
    }
}
