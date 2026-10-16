package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * {@link MechHullDownScene} recorded at 1920x1080, 100% scale: the first seed of the open control
 * and of the Hound behind its berm, from the same plays the verdicts are read from. The close view
 * frames the Hound and its parapet; the caption carries what each section has taken.
 */
public final class MechHullDownSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public MechHullDownSceneSnapshotSuite() {
        super(new MechHullDownScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override
    protected float[] closeView(String loopId) {
        return new float[] {14f, 10.5f, 14f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override
    protected int[] stillFrames(String loopId) {
        // Early contact, mid-window and the end of the window.
        return new int[] {10, 75, 149};
    }
}
