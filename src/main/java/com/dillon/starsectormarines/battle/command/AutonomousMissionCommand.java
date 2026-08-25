package com.dillon.starsectormarines.battle.command;

/** Frame-only production strategy contract used by migrated mission commanders. */
public interface AutonomousMissionCommand<F extends CommandFrame, D>
        extends CommandStrategy {

    /** Stable issuer id used by the assignment ledger and diagnostics. */
    String strategyId();

    CommandPlan<D> plan(F frame);

    /** Allows typed diagnostics to reconcile against authoritative arbiter results. */
    default CommanderSnapshot<D> reconcile(CommanderSnapshot<D> snapshot) {
        return snapshot;
    }

    void publish(CommanderSnapshot<D> snapshot);
}
