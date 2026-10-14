package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

import java.util.List;

/**
 * The capability-planned breach frames, from the same run that produced the verdicts: the cannon and the charges loops and the
 * combined-arms assault (mortar section, called air strikes and a charge breach together), in the window
 * where the company positions its base of fire, suppresses and enters, at 1920x1080 and 100% scale, whole map and a
 * close view of the fort's west face. Every close frame is also written as a PNG still so a moment can be read.
 */
public final class AssaultMusterSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public AssaultMusterSceneSnapshotSuite() {
        super(new AssaultMusterScene(List.of("breach-cannon", "breach-charges", "combined-arms"), 2300, 5800, 150), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    /** The fort west face where the breach is cut: the wall section, the ground in front of it and the nests that cover it. */
    @Override protected float[] closeView(String loopId) {
        // The combined-arms assault is read from the base of fire to the wall, so its view takes in both.
        if (loopId.startsWith("combined-arms")) return new float[] {122f, 224f, 100f};
        return new float[] {146f, 227f, 70f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        int[] all = new int[40];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return all;
    }
}
