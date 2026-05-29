package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.ClearZone;
import com.dillon.starsectormarines.battle.decision.goap.action.HoldZone;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Commander-driven compound capture goal. Fires for squads whose
 * {@link Squad#assignedObjective} has {@link AssignmentKind#SECURE_COMPOUND}
 * — written by {@link com.dillon.starsectormarines.battle.command.ConquestCommand}
 * when an uncaptured compound is at or behind the squad's forward position.
 *
 * <p>Three-phase plan: push to the compound's zone (zone-graph BFS),
 * clear enemies ({@link ClearZone}), then hold until the capture timer
 * completes ({@link HoldZone}). The hold phase keeps marines present in the
 * zone so {@link com.dillon.starsectormarines.battle.command.compound.CompoundCaptureSystem}
 * accumulates toward {@link CompoundService.CompoundState#MARINE_HELD}.
 *
 * <p>Relevance 0.9 — above {@link ClearAssignedZoneGoal}'s 0.8 so a squad
 * assigned both a generic zone clear and a compound capture prefers the
 * compound (in practice the commander issues one or the other, not both).
 * Below {@link SecureObjectiveZone}'s 1.0 so a planter squad still follows
 * its unit-level mission.
 */
public final class SecureCompoundGoal implements Goal {

    public static final SecureCompoundGoal INSTANCE = new SecureCompoundGoal();

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.ZONE_CLEAR, true);

    private SecureCompoundGoal() {}

    @Override public String name() { return "SecureCompound"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null) return 0f;
        if (assignment.kind() != AssignmentKind.SECURE_COMPOUND) return 0f;
        int targetZone = assignment.targetZoneId();
        if (targetZone < 0) return 0f;
        TacticalNode node = assignment.targetNode();
        if (node == null) return 0f;
        if (state.get(Predicate.MORALE_BROKEN)) return 0f;
        CompoundService.Record record = sim.getCompoundService().getRecord(node);
        if (record == null) return 0f;
        if (record.state == CompoundService.CompoundState.MARINE_HELD) return 0f;
        int currentZone = ZoneQueries.squadCurrentZone(squad, sim);
        if (currentZone < 0) return 0f;
        if (currentZone != targetZone
                && ZoneQueries.zonePathBfs(currentZone, targetZone, sim).size() < 2) return 0f;
        return 0.9f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null || assignment.kind() != AssignmentKind.SECURE_COMPOUND) return null;
        int to = assignment.targetZoneId();
        TacticalNode node = assignment.targetNode();
        if (node == null) return null;

        if (planEndsWithHoldZone(squad.currentPlan, to)) {
            return squad.currentPlan;
        }

        int from = ZoneQueries.squadCurrentZone(squad, sim);
        if (from == to) {
            Faction enemy = squad.faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
            List<SquadPlan.Step> steps = new ArrayList<>(2);
            if (!ZoneQueries.zoneClear(to, enemy, sim)) {
                steps.add(new SquadPlan.Step(new ClearZone(to)));
            }
            steps.add(new SquadPlan.Step(new HoldZone(to, node)));
            return new SquadPlan(steps);
        }

        return synthesizeSecurePlan(from, to, node, sim);
    }

    private static SquadPlan synthesizeSecurePlan(int fromZone, int toZone, TacticalNode node,
                                                   BattleView sim) {
        List<Integer> path = ZoneQueries.zonePathBfs(fromZone, toZone, sim);
        if (path.size() < 2) return null;

        var graph = sim.getZoneGraph();
        var grid = sim.getGrid();
        List<SquadPlan.Step> steps = new ArrayList<>(2 * path.size());
        for (int i = 1; i < path.size(); i++) {
            int zoneId = path.get(i);
            var zone = graph.zoneById(zoneId);
            if (zone == null) return null;
            if (i < path.size() - 1
                    && zone.getCellCount() == 1
                    && grid.isDoorwayAt(zone.getCellIndices()[0])) continue;
            steps.add(new SquadPlan.Step(
                    com.dillon.starsectormarines.battle.decision.goap.action.EnterZone.forZone(zone, grid)));
            steps.add(new SquadPlan.Step(new ClearZone(zoneId)));
        }
        steps.add(new SquadPlan.Step(new HoldZone(toZone, node)));
        return new SquadPlan(steps);
    }

    private static boolean planEndsWithHoldZone(SquadPlan plan, int targetZoneId) {
        if (plan == null || plan.isComplete()) return false;
        List<SquadPlan.Step> steps = plan.steps();
        if (steps.isEmpty()) return false;
        var terminal = steps.get(steps.size() - 1).action;
        return terminal instanceof HoldZone hz && hz.targetZoneId() == targetZoneId;
    }
}
