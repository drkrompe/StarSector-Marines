package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ColonyArchiveObjective;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Splits the expedition between the survivor route and physical archive. */
public final class SilentColonyCommand implements MissionCommand {

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
    public void tick(BattleView sim) {
        int archiveSquad = archive.isRecovered()
                ? -1 : firstLiveMarineSquad(sim);
        int[] survivorTarget = survivorTarget(sim);
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || squad.aliveMembers <= 0) {
                continue;
            }
            if (squad.id == archiveSquad) {
                assignArchive(squad);
            } else if (survivorTarget != null) {
                assignSurvivors(squad, survivorTarget);
            } else if (!archive.isRecovered()) {
                assignArchive(squad);
            } else {
                squad.assignedObjective = null;
            }
        }
    }

    private void assignArchive(Squad squad) {
        ObjectiveAssignment current = squad.assignedObjective;
        if (current == null || current.kind() != AssignmentKind.CLEAR_ZONE
                || current.targetZoneId() != archive.zoneId()) {
            squad.assignedObjective = ObjectiveAssignment.clearZone(
                    squad.id, archive.zoneId());
        }
    }

    private static void assignSurvivors(Squad squad, int[] target) {
        ObjectiveAssignment current = squad.assignedObjective;
        if (current == null || current.kind() != AssignmentKind.ESCORT
                || current.targetCellX() != target[0]
                || current.targetCellY() != target[1]) {
            squad.assignedObjective = ObjectiveAssignment.escort(
                    squad.id, target[0], target[1]);
        }
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
