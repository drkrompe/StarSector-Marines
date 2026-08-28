package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.sim.BattleView;

/** Full attacker-owned Raid objective projection. */
public record RaidCommandFacts(
        String targetId,
        String targetName,
        int targetCellX,
        int targetCellY,
        int targetZoneId,
        int egressCellX,
        int egressCellY,
        float serviceProgress,
        float serviceDuration,
        boolean marineServicing,
        boolean targetSecured,
        boolean complete,
        RaidObjective.Phase phase) {

    static RaidCommandFacts freeze(BattleView sim) {
        RaidObjective objective = objective(sim);
        return new RaidCommandFacts(objective.targetId(), objective.targetName(),
                objective.targetCellX(), objective.targetCellY(),
                objective.targetZoneId(), objective.egressCellX(),
                objective.egressCellY(), objective.serviceProgress(),
                objective.serviceDuration(), objective.marineServicing(),
                objective.targetSecured(), objective.isComplete(),
                objective.phase());
    }

    static RaidObjective objective(BattleView sim) {
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RaidObjective raid) return raid;
        }
        throw new IllegalStateException("Raid command requires RaidObjective");
    }
}
