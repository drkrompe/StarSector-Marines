package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ColonyArchiveObjective;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Splits the expedition between the survivor route and physical archive. */
public final class SilentColonyCommand implements MissionCommand {

    static final String ISSUER = "silent-colony";

    private final CivilianEvacuationPlacement placement;
    private final ColonyArchiveObjective archive;

    public SilentColonyCommand(CivilianEvacuationPlacement placement,
                               ColonyArchiveObjective archive) {
        if (placement == null || archive == null) {
            throw new IllegalArgumentException("expedition objectives required");
        }
        this.placement = placement;
        this.archive = archive;
    }

    @Override
    public Faction faction() {
        return Faction.MARINE;
    }

    @Override
    public void tick(BattleView sim, SquadDirectiveControl directives) {
        int archiveSquad = archive.isRecovered()
                ? -1 : firstLiveMarineSquad(sim);
        int[] survivorTarget = survivorTarget(sim);
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || squad.aliveMembers <= 0) {
                continue;
            }
            if (squad.id == archiveSquad) {
                assignArchive(squad, directives);
            } else if (survivorTarget != null) {
                assignSurvivors(squad, survivorTarget, directives);
            } else if (!archive.isRecovered()) {
                assignArchive(squad, directives);
            } else {
                directives.releaseSquadCommand(squad.id, ISSUER,
                        "expedition objectives complete");
            }
        }
    }

    private void assignArchive(Squad squad,
                               SquadDirectiveControl directives) {
        directives.assignSquadCommand(ObjectiveAssignment.clearZone(
                        squad.id, archive.zoneId()),
                CommandAuthority.MISSION_COMMAND, ISSUER,
                "recover sealed colony archive");
    }

    private static void assignSurvivors(Squad squad, int[] target,
                                        SquadDirectiveControl directives) {
        directives.assignSquadCommand(ObjectiveAssignment.escort(
                        squad.id, target[0], target[1]),
                CommandAuthority.MISSION_COMMAND, ISSUER,
                "secure and escort colony survivors");
    }

    private int[] survivorTarget(BattleView sim) {
        if (!sim.isCivilianEvacuationTriggered()) {
            return new int[]{placement.shelterApproachX,
                    placement.shelterApproachY};
        }
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        int sumX = 0;
        int sumY = 0;
        int count = 0;
        for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
            long id = tracker.entityIdAt(i);
            if (tracker.state(id) != CivilianEvacuationTracker.State.ACTIVE
                    || sim.resolveUnit(id) == 0L) continue;
            sumX += sim.world().cellX(id);
            sumY += sim.world().cellY(id);
            count++;
        }
        return count > 0
                ? new int[]{Math.round((float) sumX / count),
                Math.round((float) sumY / count)}
                : null;
    }

    private static int firstLiveMarineSquad(BattleView sim) {
        int first = Integer.MAX_VALUE;
        for (Squad squad : sim.getSquads()) {
            if (squad.faction == Faction.MARINE && squad.aliveMembers > 0) {
                first = Math.min(first, squad.id);
            }
        }
        return first == Integer.MAX_VALUE ? -1 : first;
    }
}
