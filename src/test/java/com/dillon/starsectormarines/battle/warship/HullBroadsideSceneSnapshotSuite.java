package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The broadside exchange's loops (1920x1080), from the same run that produced their verdicts and readings. */
public final class HullBroadsideSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public HullBroadsideSceneSnapshotSuite() {
        super(new HullBroadsideScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    /** The close view is shown to people, so it is recorded at the full 1920x1080 too. */
    @Override protected float closeViewScale() { return 1f; }

    /** Close on the eastern hull, the one the GIF is about. */
    @Override protected float[] closeView(String loopId) {
        return new float[]{HullBroadsideScene.MID_X + 17f, HullBroadsideScene.MID_Y, 44f};
    }
}
