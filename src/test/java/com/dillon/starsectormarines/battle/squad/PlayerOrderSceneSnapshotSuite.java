package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * {@link PlayerOrderScene} recorded: the click going in, the squad turning off
 * its mission axis to answer it, and the mission coming back afterwards.
 *
 * <p>Three loops of one order. <b>The picture carries this scene better than
 * most</b>, because the failure it guards against has a shape: a squad that
 * ignores the click keeps heading east, and a squad that never hands the order
 * back stops dead where the click landed. Both are visible in a frame. The
 * tick numbers still come from the printed verdicts — "answered on the next
 * tick" is not something a fifteen-tick frame cadence can show.
 *
 * <p>The arena is three cells wide for every one it is tall, so the frame is
 * sized to match rather than letterboxing a corridor into a square.
 */
public final class PlayerOrderSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 320;

    public PlayerOrderSceneSnapshotSuite() {
        super(new PlayerOrderScene(), WIDTH, HEIGHT);
    }
}
