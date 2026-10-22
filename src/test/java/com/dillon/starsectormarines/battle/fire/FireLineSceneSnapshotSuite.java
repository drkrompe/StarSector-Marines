package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The fire-line loops: the whole strip, and the forest edge at play zoom as the front runs into it. */
public final class FireLineSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public FireLineSceneSnapshotSuite() {
        super(new FireLineScene(), 1920, 1080);
    }

    @Override protected float[] closeView(String loopId) {
        return new float[]{FireLineScene.MEADOW_END + 6f, FireLineScene.HEIGHT / 2f, 60f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected boolean colourAnimation() { return true; }

    @Override protected int[] stillFrames(String loopId) { return new int[]{9, 11, 13, 16, 20, 30, 81}; }
}
