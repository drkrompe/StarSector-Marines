package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * The strain skirmish from the run that produced its verdicts. The map is one
 * screen wide, so the whole-map recording is the game's normal battle zoom at
 * 1920x1080; the close recording is twice that, for reading the bodies.
 */
public final class SwarmStrainSkirmishSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public SwarmStrainSkirmishSnapshotSuite() {
        super(new SwarmStrainSkirmishScene(), 1920, 1080);
    }

    /** Lobs, splashes and deaths are what the review is about. */
    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean colourAnimation() { return true; }

    /** Full resolution for the close view too: it is an art review. */
    @Override protected float closeViewScale() { return 1f; }

    /** The contact line, at twice the battle zoom. */
    @Override protected float[] closeView(String loopId) {
        return loopId.equals("main") ? new float[]{62f, 31.5f, 56f} : null;
    }

    @Override protected int[] stillFrames(String loopId) {
        return loopId.equals("main") || loopId.equals("main-close")
                ? new int[]{20, 60, 100, 140, 180, 224} : new int[0];
    }
}
