package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * {@link SmallArmsWearScene} recorded at 1920x1080, 100% scale, from the same play the verdicts are read from:
 * crates going to splinters, sandbags tearing, and a wall that rifles do not mark until anti-materiel rounds open
 * it. The arena is forty cells across, so the whole map is already a close view. A still at the opening, the
 * middle and the end of each loop lets the state of the cover be read at rest.
 */
public final class SmallArmsWearSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public SmallArmsWearSceneSnapshotSuite() {
        super(new SmallArmsWearScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected int[] stillFrames(String loopId) {
        return new int[] {0, 18, 37};
    }
}
