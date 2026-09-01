package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/**
 * {@link ProsecutionHoldScene} recorded: the squad crossing to its room with
 * an enemy standing off to the north that nobody can shoot at, the same squad
 * rooted to its start with the fall-through off, and the same squad taking
 * firing positions when the enemy is near enough to be worth them.
 *
 * <p><b>The freeze is the one finding here that a picture states better than a
 * number.</b> Twelve marines standing still is what the live dumps described
 * and what the control loop is: nothing happens, in a frame, for the length of
 * the recording, beside a subject loop covering most of the map. The tick
 * counts still come from the printed verdicts.
 */
public final class ProsecutionHoldSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 880;
    private static final int HEIGHT = 560;

    public ProsecutionHoldSceneSnapshotSuite() {
        super(new ProsecutionHoldScene(), WIDTH, HEIGHT);
    }
}
