package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.FightingPositionReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;

/**
 * {@link CompoundApronScene} recorded at 1920x1080 and 100% scale, from the same play its verdicts are read from, with a
 * close view on the barracks door: the quiet apron empty, the pressed apron manned and dug in, and the breached
 * garrison going inside. The fighting-position layer marks the apron's run green, claimed cells white and dug-in
 * marines magenta.
 */
public final class CompoundApronSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public CompoundApronSceneSnapshotSuite() {
        super(new CompoundApronScene(), 1920, 1080);
    }

    @Override
    protected ReviewAnnotations annotations(BattleSimulation simulation) {
        return FightingPositionReviewAnnotations.forBattle(simulation);
    }

    @Override
    protected float[] closeView(String loopId) {
        return loopId.equals("quiet") ? null : new float[]{80f, 60f, 48f};
    }

    /** The close view is shown to the owner, so it is drawn at the full 1920x1080 too. */
    @Override
    protected float closeViewScale() {
        return 1f;
    }
}
