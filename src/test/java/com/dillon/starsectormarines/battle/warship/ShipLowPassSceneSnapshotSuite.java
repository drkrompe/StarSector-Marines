package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * A capital's low pass at 1920x1080: the run down a line of defenders, the shadow lying close and
 * sharp under a hull that is skimming, the louder wake and the dust left along the line, then the
 * coast to a stop and the slow hover home. The pass loops are also recorded close in.
 */
public final class ShipLowPassSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public ShipLowPassSceneSnapshotSuite() {
        super(new ShipLowPassScene(), WIDTH, HEIGHT);
    }

    /** The shadow is the altitude cue; a skimming hull's lies close and sharp. */
    @Override protected boolean showUnitShadows() { return true; }

    /** The impacts along the line are the point of the pass. */
    @Override protected boolean showImpactEffects() { return true; }

    /** Soft dust and vapour are most of the picture; the compact palette bands them. */
    @Override protected boolean colourAnimation() { return true; }

    /** Close on the middle of the line, where the defenders stand and the guns are working. */
    @Override protected float[] closeView(String loopId) {
        return loopId.equals("pass") ? new float[]{110f, 50f, 100f} : null;
    }

    /** Full-colour stills of the run: the start of the line, mid-line, the far end, and the dust left behind. */
    @Override protected int[] stillFrames(String loopId) {
        return loopId.startsWith("pass") ? new int[]{3, 14, 32, 48, 60, 68, 80} : new int[0];
    }
}
