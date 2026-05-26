package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.ClearZone;
import com.dillon.starsectormarines.battle.ai.goap.actions.HoldZone;
import com.dillon.starsectormarines.battle.ai.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.compound.CompoundService;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

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
 * zone so {@link com.dillon.starsectormarines.battle.compound.CompoundCaptureSystem}
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
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
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
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleSimulation sim) {
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
                                                   BattleSimulation sim) {
        List<Integer> path = ZoneQueries.zonePathBfs(fromZone, toZone, sim);
        if (path.size() < 2) return null;

        var graph = sim.getZoneGraph();
        List<SquadPlan.Step> steps = new ArrayList<>(2 * path.size());
        for (int i = 1; i < path.size(); i++) {
            int zoneId = path.get(i);
            var zone = graph.zoneById(zoneId);
            if (zone == null) return null;
            steps.add(new SquadPlan.Step(
                    com.dillon.starsectormarines.battle.ai.goap.actions.EnterZone.forZone(zone, sim.getGrid())));
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
