package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.sim.BattleView;

/** Defender-owned geometry and identity-free Raid installation alarm. */
public record RaidDefenderCommandFacts(
        String targetId,
        String targetName,
        int targetCellX,
        int targetCellY,
        int targetZoneId,
        boolean targetSecured,
        boolean alarmActive,
        int alarmRaisedTick) {

    static RaidDefenderCommandFacts freeze(BattleView sim) {
        RaidObjective objective = RaidCommandFacts.objective(sim);
        RaidObjective.TargetAlarm alarm = objective.defenderAlarm();
        return new RaidDefenderCommandFacts(objective.targetId(),
                objective.targetName(), objective.targetCellX(),
                objective.targetCellY(), objective.targetZoneId(),
                objective.targetSecured(), alarm.active(), alarm.raisedTick());
    }
}
