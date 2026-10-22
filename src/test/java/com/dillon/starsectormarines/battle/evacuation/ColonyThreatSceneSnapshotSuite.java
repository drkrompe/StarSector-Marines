package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * The ColonyThreat loop with shots, impacts and shadows drawn. The whole map is about play zoom (34 px a cell); the close
 * view is 64 px a cell on the ground between the colonies, at the full frame size, as evidence is shown.
 */
public final class ColonyThreatSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public ColonyThreatSceneSnapshotSuite() {
        super(new ColonyThreatScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected boolean colourAnimation() { return true; }

    @Override protected float closeViewScale() { return 1f; }

    @Override
    protected float[] closeView(String loopId) {
        return new float[]{ColonyThreatScene.CREEP_X + 2f, ColonyThreatScene.CREEP_Y, 30f};
    }

    @Override
    protected int[] stillFrames(String loopId) {
        int[] frames = new int[36];
        for (int i = 0; i < frames.length; i++) frames[i] = i * 4;
        return frames;
    }
}
