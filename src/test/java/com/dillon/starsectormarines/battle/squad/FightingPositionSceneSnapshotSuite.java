package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.FightingPositionReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;

/**
 * {@link FightingPositionScene} recorded at 1920x1080 and 100% scale: a squad in a trench under rifle fire with
 * fighting positions on and the same squad with them off, the squad filling an empty trench, and a
 * twelve-marine squad sharing a twelve-cell run with a two-marine squad. The frames carry the
 * fighting-position layer: position cells in green, claimed cells outlined in white, dug-in marines in magenta.
 */
public final class FightingPositionSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public FightingPositionSceneSnapshotSuite() {
        super(new FightingPositionScene(), WIDTH, HEIGHT);
    }

    /** One still per loop, early in the fight, once the squad has settled. */
    @Override
    protected int[] stillFrames(String loopId) {
        return new int[]{10};
    }

    @Override
    protected ReviewAnnotations annotations(BattleSimulation simulation) {
        return FightingPositionReviewAnnotations.forBattle(simulation);
    }
}
