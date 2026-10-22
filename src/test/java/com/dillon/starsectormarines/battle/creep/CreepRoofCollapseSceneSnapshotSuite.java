package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The CreepRoofCollapse loops with roofs drawn: the whole field, and the near hall close up at full frame size. */
public final class CreepRoofCollapseSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public CreepRoofCollapseSceneSnapshotSuite() {
        super(new CreepRoofCollapseScene(), 1920, 1080);
    }

    @Override protected boolean showRoofs() { return true; }

    @Override protected float[] closeView(String loopId) { return CreepRoofCollapseScene.nearHallView(); }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected int[] stillFrames(String loopId) { return new int[]{16, 21, 26, 47}; }
}
