package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.TacticalScoring.PursuitDecision;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

/** Shared Story-I target-release policy for generic infantry pursuit. */
final class EngagementDiscipline {

    private EngagementDiscipline() {}

    /**
     * Returns the target generic Engage/Approach movement may pursue, or
     * {@code 0L} after latching a squad hold. The ordinary target picker's
     * blind fallback remains available for initial acquisition; once picked,
     * the explicit pursuit assessment prevents that fallback from immediately
     * reacquiring an unsafe runner.
     */
    static long targetForPursuit(long member, Squad squad, BattleControl sim) {
        if (squad.engagementDisciplineHold) {
            sim.world().setTargetId(member, 0L);
            sim.clearPath(member);
            return 0L;
        }

        TacticalScoring scoring = sim.getTacticalScoring();
        long target = sim.targetOf(member);
        if (target == 0L || sim.resolveUnit(target) == 0L) {
            target = scoring.findBestTarget(member);
            sim.world().setTargetId(member, target);
        }
        if (target == 0L) return 0L;

        PursuitDecision decision = scoring.assessPursuit(member, target, squad);
        if (decision == PursuitDecision.KEEP) return target;

        long alternative = scoring.findBestVisibleLowDensityTarget(member, target, squad);
        if (alternative != 0L) {
            sim.world().setTargetId(member, alternative);
            return alternative;
        }

        // RETARGET can also mean a visible candidate was closer but failed the
        // low-density gate. Keeping the current non-clustered target is safer
        // than switching into that formation.
        if (decision == PursuitDecision.RETARGET) return target;

        int density = scoring.threatDensityAt(target, squad);
        squad.holdEngagementLine(target, density);
        clearSquadPursuit(squad, sim);
        return 0L;
    }

    private static void clearSquadPursuit(Squad squad, BattleControl sim) {
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long squadmate = sim.squadMemberAt(squad.id, i);
            if (!squad.participatesInPlan(squadmate)) continue;
            sim.world().setTargetId(squadmate, 0L);
            sim.clearPath(squadmate);
        }
    }

    /**
     * Re-evaluates a latched hold from one member's viewpoint. Any member
     * seeing a safe alternative releases the squad; so do target death and
     * dispersal of the rejected cluster.
     */
    static boolean canReleaseHold(long member, Squad squad, BattleView sim) {
        long rejected = squad.engagementDisciplineTargetId;
        if (rejected == 0L || sim.resolveUnit(rejected) == 0L) return true;

        TacticalScoring scoring = sim.getTacticalScoring();
        int density = scoring.threatDensityAt(rejected, squad);
        squad.engagementDisciplineThreatDensity = density;
        if (density < TacticalScoring.HIGH_THREAT_DENSITY_COUNT) return true;
        return scoring.findBestVisibleLowDensityTarget(member, rejected, squad) != 0L;
    }
}
