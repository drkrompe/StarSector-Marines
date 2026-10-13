package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * A capital's entry through the cloud deck and its climb back out, at 1920x1080,
 * each also recorded close in. The shadow is the altitude cue, so it is drawn.
 */
public final class ShipArrivalSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public ShipArrivalSceneSnapshotSuite() {
        super(new ShipArrivalScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showUnitShadows() { return true; }

    /** Soft cloud, glow and dust are the whole picture here, and the compact palette bands them. */
    @Override protected boolean colourAnimation() { return true; }

    /** Full-colour stills of the moments worth reading closely: the break-through, the settle, the ring, the climb. */
    @Override protected int[] stillFrames(String loopId) {
        return loopId.endsWith("close") ? new int[]{2, 10, 24, 40, 48, 62} : new int[0];
    }

    /** Close on the approach: from the edge to a little past the station. */
    @Override protected float[] closeView(String loopId) {
        return new float[]{34f, 48f, 80f};
    }
}
