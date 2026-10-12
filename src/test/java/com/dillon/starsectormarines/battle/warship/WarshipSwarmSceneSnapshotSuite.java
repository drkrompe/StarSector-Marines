package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The warship swarm scene's frames, from the same run that produced its verdicts. */
public final class WarshipSwarmSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    public WarshipSwarmSceneSnapshotSuite() {
        super(new WarshipSwarmScene(), WIDTH, HEIGHT);
    }

    /** The swarm is its landings and its bursts, so the frames carry the game's own impact particles. */
    @Override protected boolean showImpactEffects() { return true; }

    /** The split and the sheaf are small against a whole 160-cell map, so every loop is also recorded close on the ground it falls on. */
    @Override protected float[] closeView(String loopId) {
        return loopId.equals("hurricane-volley") || loopId.equals("rack-cap") ? null : new float[]{66f, 45f, 72f};
    }

    /** The hulls hover well above the ground and a shadow lying clear of them is what says so. */
    @Override protected boolean showUnitShadows() { return true; }
}
