package com.dillon.starsectormarines.battle.smoke;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * {@link SmokeScreenScene} recorded at 1920x1080 and 100% scale: the whole map, and a close view of the screen between the
 * sappers' approach and the nest, with the smoke layer drawn so the cloud and its thinning are in the frame.
 */
public final class SmokeScreenSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public SmokeScreenSceneSnapshotSuite() {
        super(new SmokeScreenScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    /** The ground between the sappers' approach and the nest: the cloud, the nest and the base of fire to its north-west. */
    @Override protected float[] closeView(String loopId) {
        return new float[] {62f, 24f, 46f};
    }

    @Override protected float closeViewScale() { return 1f; }
}
