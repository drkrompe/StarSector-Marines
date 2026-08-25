package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;

/** Frame-only production strategy contract used by migrated mission commanders. */
public interface AutonomousMissionCommand<F extends CommandFrame, D>
        extends MissionCommand {

    /** Stable issuer id used by the assignment ledger and diagnostics. */
    String strategyId();

    F freeze(BattleView sim, CommandTopology topology,
             CommandAssignmentSnapshot assignments);

    CommandPlan<D> plan(F frame);

    void publish(CommanderSnapshot<D> snapshot);

    @Override
    default void tick(BattleView sim) {
        CommanderService.runSingle(this, sim);
    }
}
