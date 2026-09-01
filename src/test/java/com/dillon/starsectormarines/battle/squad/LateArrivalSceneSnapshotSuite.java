package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * {@link LateArrivalScene} recorded: the squad stepping off short-handed, the
 * rest of it landing behind, and four marines walking the length of the map to
 * catch up.
 *
 * <p><b>The picture carries the negative better than the numbers do.</b> "No
 * shot was authored at anybody out of reach" is a count; four cyan markers
 * filing east past a red pair twelve cells north of them, without one of them
 * turning aside, is the behaviour itself.
 *
 * <p>The arena is three cells wide for every one it is tall, so the frame is
 * sized to match rather than letterboxing a corridor into a square.
 */
public final class LateArrivalSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 320;

    public LateArrivalSceneSnapshotSuite() {
        super(new LateArrivalScene(), WIDTH, HEIGHT);
    }
}
