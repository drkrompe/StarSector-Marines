package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * The SporeColonyFire loops close on the bore, with the shots, impacts and shadows drawn: it is about how the
 * anti-air looks. The close view is kept at the full frame size, because evidence is shown at 1920x1080.
 */
public final class SporeColonyFireSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    /** Thirty cells across a 1920-wide frame: 64 px per cell. */
    private static final float CELLS_ACROSS = 30f;

    public SporeColonyFireSceneSnapshotSuite() {
        super(new SporeColonyFireScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected boolean colourAnimation() { return true; }

    @Override protected float closeViewScale() { return 1f; }

    @Override
    protected float[] closeView(String loopId) {
        // A little above the bore: the craft is drawn up to three cells higher than it flies.
        return new float[]{SporeColonyFireScene.CENTRE + 0.5f, SporeColonyFireScene.CENTRE + 2.0f, CELLS_ACROSS};
    }

    @Override
    protected int[] stillFrames(String loopId) {
        int[] frames = new int[40];
        for (int i = 0; i < frames.length; i++) frames[i] = 30 + i;
        return frames;
    }
}
