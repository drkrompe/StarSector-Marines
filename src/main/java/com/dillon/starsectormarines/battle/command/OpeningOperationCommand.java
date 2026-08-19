package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Small-force advance order used by opening operations. Mobile squads close on
 * the nearest hostile combatant; authored local garrisons can be left in place.
 */
public final class OpeningOperationCommand implements MissionCommand {

    private final Faction faction;
    private final Faction opponent;
    private final boolean preserveLocalGarrisons;

    public OpeningOperationCommand(Faction faction, Faction opponent,
                                   boolean preserveLocalGarrisons) {
        if (faction == null || opponent == null || faction == opponent) {
            throw new IllegalArgumentException("distinct command factions are required");
        }
        this.faction = faction;
        this.opponent = opponent;
        this.preserveLocalGarrisons = preserveLocalGarrisons;
    }

    @Override
    public Faction faction() {
        return faction;
    }

    @Override
    public void tick(BattleView sim) {
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != faction || squad.aliveMembers <= 0) continue;
            if (preserveLocalGarrisons && squad.assignedNode != null
                    && squad.assignedNode.defaultGuard == faction) {
                squad.assignedObjective = null;
                continue;
            }
            int targetZone = nearestOpponentZone(squad, sim);
            if (targetZone < 0) {
                squad.assignedObjective = null;
                continue;
            }
            ObjectiveAssignment current = squad.assignedObjective;
            if (current == null || current.kind() != AssignmentKind.CLEAR_ZONE
                    || current.targetZoneId() != targetZone) {
                squad.assignedObjective = ObjectiveAssignment.clearZone(
                        squad.id, targetZone);
            }
        }
    }

    private int nearestOpponentZone(Squad squad, BattleView sim) {
        long nearest = 0L;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long candidate = sim.liveUnitAt(i);
            UnitType type = sim.identity().type(candidate);
            if (sim.identity().faction(candidate) != opponent || !type.combatant) continue;
            float dx = sim.world().x(candidate) - squad.centroidX;
            float dy = sim.world().y(candidate) - squad.centroidY;
            float distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest == 0L ? -1 : sim.getZoneGraph().zoneIdAt(
                sim.world().cellX(nearest), sim.world().cellY(nearest));
    }
}
