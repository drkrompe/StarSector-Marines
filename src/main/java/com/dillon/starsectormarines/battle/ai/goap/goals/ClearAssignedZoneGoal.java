package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;

/**
 * Commander-driven push-into-zone goal. Fires for squads whose
 * {@link Squad#assignedObjective} has
 * {@link AssignmentKind#CLEAR_ZONE} — written by a
 * {@link com.dillon.starsectormarines.battle.command.MissionCommand} during
 * its slow tick to spread squads across objective-bearing zones (e.g. the
 * three charge-site zones in a SABOTAGE mission, so cover-fire squads
 * don't all dogpile the nearest fight).
 *
 * <p>Sister to {@link SecureObjectiveZone}: both live in the
 * {@link Priority#MISSION} bucket, both synthesize via
 * {@link ZoneQueries#synthesizeZonePushPlan}. The difference is the
 * <em>source</em> of the target zone — {@link SecureObjectiveZone} reads a
 * unit-level planter's {@code Unit.assignedObjective}; this goal reads the
 * squad-level commander assignment. A squad can in principle have both;
 * {@link Goal#pickMostRelevant} resolves by raw relevance score within the
 * MISSION bucket (the planter-driven goal currently outscores this one,
 * keeping mid-route planter teams on their original target).
 *
 * <p>Custom-plan goal — bypasses the backward-chaining planner via
 * {@link #customPlan}. {@link #desiredState} is set for diagnostic
 * symmetry with the rest of the goal library but isn't load-bearing
 * (the {@code ZONE_CLEAR} predicate evaluator stays {@code STUB_FALSE} —
 * the action library tracks zone-clear via {@link ZoneQueries#zoneClear}).
 */
public final class ClearAssignedZoneGoal implements Goal {

    public static final ClearAssignedZoneGoal INSTANCE = new ClearAssignedZoneGoal();

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.ZONE_CLEAR, true);

    private ClearAssignedZoneGoal() {}

    @Override public String name() { return "ClearAssignedZone"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null) return 0f;
        if (assignment.kind() != AssignmentKind.CLEAR_ZONE) return 0f;
        int targetZone = assignment.targetZoneId();
        if (targetZone < 0) return 0f;
        int currentZone = ZoneQueries.squadCurrentZone(squad, sim);
        if (currentZone < 0 || currentZone == targetZone) return 0f;
        // Reachability gate — disconnected target zone means the commander's
        // assignment is unrealizable; fall through to ENGAGEMENT defaults so
        // the squad still does something useful while waiting for re-assignment.
        if (ZoneQueries.zonePathBfs(currentZone, targetZone, sim).size() < 2) return 0f;
        // Below SecureObjectiveZone's 1.0 — a squad with both a planter
        // (unit-level objective) and a commander assignment keeps following
        // the planter's target. The commander's task is the fallback for
        // squads without their own unit-level objective.
        return 0.8f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleSimulation sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null || assignment.kind() != AssignmentKind.CLEAR_ZONE) return null;
        int from = ZoneQueries.squadCurrentZone(squad, sim);
        int to = assignment.targetZoneId();
        return ZoneQueries.synthesizeZonePushPlan(from, to, sim);
    }
}
