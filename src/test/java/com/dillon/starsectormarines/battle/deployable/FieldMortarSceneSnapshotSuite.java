package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

import java.util.List;

/**
 * The field mortar's frames, from the same run that produced its verdicts: the {@code mortar-team} loop, the
 * {@code rushed} loop and the {@code section-march} loop (a twelve-marine squad halting a four-person crew while the rest
 * is leashed, then packing up and moving on) at 1920x1080 and 100% scale, whole map plus a close view on the section — the set-up, the
 * tube firing across the standoff, the shell in flight and the impacts on the nest — with every close frame written
 * as a PNG still.
 */
public final class FieldMortarSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public FieldMortarSceneSnapshotSuite() {
        super(new FieldMortarScene(List.of("mortar-team", "rushed", "section-march"), 60, 1500, 15), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    /** Midway between the section and the gate it is shelling, wide enough to hold both. */
    @Override protected float[] closeView(String loopId) {
        int[] s = FieldMortarScene.lastSection();
        return new float[] {(s[0] + s[2]) / 2f, (s[1] + s[3]) / 2f, 70f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        int[] all = new int[40];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return all;
    }
}
