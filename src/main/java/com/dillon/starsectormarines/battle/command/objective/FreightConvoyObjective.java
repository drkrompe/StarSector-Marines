package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.setup.ThunderRunSequence;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Territorial gains release travel; only delivered freight can complete the operation. */
public final class FreightConvoyObjective implements Objective {
    private final ThunderRunSequence column;
    private boolean complete, failed;
    public FreightConvoyObjective(ThunderRunSequence column) { this.column = column; }
    @Override public Faction owningFaction() { return Faction.MARINE; }
    @Override public void tick(BattleView sim) {
        if (complete || failed) return;
        failed = column.resolved() && !column.successful();
        complete = column.successful() && ObjectiveFactionPresence.anyInPlay(sim, Faction.MARINE);
    }
    @Override public boolean isComplete() { return complete; }
    @Override public boolean isFailed() { return failed; }
    @Override public String displayName() { return column.status(); }
}
