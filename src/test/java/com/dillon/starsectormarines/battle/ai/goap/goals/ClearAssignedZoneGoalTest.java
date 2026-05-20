package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.ClearZone;
import com.dillon.starsectormarines.battle.ai.goap.actions.EnterZone;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic-grid coverage for {@link ClearAssignedZoneGoal}. Mirrors
 * {@link SecureObjectiveZoneTest}'s structure since the customPlan logic is
 * shared through {@code ZoneQueries.synthesizeZonePushPlan} — what's tested
 * here is the relevance gating driven by {@code Squad.assignedObjective}.
 */
public class ClearAssignedZoneGoalTest {

    private static final int W = 10;
    private static final int H = 10;
    private static final int WALL_COL = 5;

    private static BattleSimulation singleDoorwaySim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == WALL_COL) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(WALL_COL, 5);
        grid.setDoorway(WALL_COL, 5, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad squadAt(int id, float centroidX, float centroidY, int aliveMembers) {
        Squad squad = new Squad(id, Faction.MARINE);
        squad.aliveMembers = aliveMembers;
        squad.centroidX = centroidX;
        squad.centroidY = centroidY;
        return squad;
    }

    @Test
    public void relevanceZeroWhenNoAssignment() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        // No assignedObjective written by any commander.
        assertEquals(0f, ClearAssignedZoneGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "no commander assignment → goal inactive");
    }

    @Test
    public void relevanceZeroWhenAssignmentIsNotClearZone() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        squad.assignedObjective = ObjectiveAssignment.support(squad.id);

        assertEquals(0f, ClearAssignedZoneGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "SUPPORT assignment is not this goal's responsibility");
    }

    @Test
    public void relevanceZeroWhenAssignedZoneIsClear() {
        // Squad in target zone, no defenders in it → goal yields. Lower
        // buckets (EliminateEnemies) pick up; commander will re-assign
        // on next slow tick.
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int leftZone = sim.getZoneGraph().zoneIdAt(2, 2);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, leftZone);

        assertEquals(0f, ClearAssignedZoneGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "target zone has no live defenders → goal inactive");
    }

    @Test
    public void relevancePositiveWhenInTargetZoneWithDefendersAlive() {
        // The oscillation-fix invariant: while we're INSIDE the target zone
        // but it still has defenders, the goal stays relevant. Without
        // this, the goal would yield on entry, a lower-priority goal
        // would move the squad differently, the centroid would drift to
        // an adjacent zone, the goal would flip back on, and the squad
        // would visibly re-enter the building each replan.
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int leftZone = sim.getZoneGraph().zoneIdAt(2, 2);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, leftZone);
        // Live defender in the same zone.
        Unit defender = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 3, 3);
        sim.addUnit(defender);

        assertTrue(ClearAssignedZoneGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f,
                "in target zone + defenders alive → goal stays relevant for the clear");
    }

    @Test
    public void customPlanEmitsClearZoneOnlyWhenAlreadyInTargetZone() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int leftZone = sim.getZoneGraph().zoneIdAt(2, 2);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, leftZone);

        SquadPlan plan = ClearAssignedZoneGoal.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan, "in-target-zone customPlan should still emit a plan");
        assertEquals(1, plan.stepCount(),
                "in-target-zone plan is a single ClearZone step — no EnterZone re-entry");
        assertTrue(plan.steps().get(0).action instanceof ClearZone);
        assertEquals(leftZone, ((ClearZone) plan.steps().get(0).action).targetZoneId());
    }

    @Test
    public void relevancePositiveWhenAssignedZoneIsReachableAndOccupied() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int rightZone = sim.getZoneGraph().zoneIdAt(8, 3);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, rightZone);
        // Live defender in the target zone — otherwise zoneClear short-
        // returns and relevance yields. The point of the test is the
        // reachability check is positive, not the empty-zone case.
        Unit defender = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 8, 3);
        sim.addUnit(defender);

        assertTrue(ClearAssignedZoneGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f,
                "reachable + occupied CLEAR_ZONE assignment should make the goal relevant");
    }

    @Test
    public void customPlanSynthesizesEnterClearAcrossBfsPath() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int rightZone = sim.getZoneGraph().zoneIdAt(8, 3);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, rightZone);

        SquadPlan plan = ClearAssignedZoneGoal.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan, "reachable assignment should produce a non-null plan");
        // Same path shape as SecureObjectiveZone: left → doorway → right
        // produces 2 zone hops × (EnterZone, ClearZone) = 4 steps.
        assertEquals(4, plan.stepCount(), "expect 2 zone hops × (EnterZone, ClearZone) = 4 steps");

        int doorwayZone = sim.getZoneGraph().zoneIdAt(WALL_COL, 5);
        assertTrue(plan.steps().get(0).action instanceof EnterZone);
        assertEquals(doorwayZone, ((EnterZone) plan.steps().get(0).action).targetZoneId());
        assertTrue(plan.steps().get(1).action instanceof ClearZone);
        assertEquals(doorwayZone, ((ClearZone) plan.steps().get(1).action).targetZoneId());
        assertTrue(plan.steps().get(2).action instanceof EnterZone);
        assertEquals(rightZone, ((EnterZone) plan.steps().get(2).action).targetZoneId());
        assertTrue(plan.steps().get(3).action instanceof ClearZone);
        assertEquals(rightZone, ((ClearZone) plan.steps().get(3).action).targetZoneId());
    }

    @Test
    public void customPlanReusesExistingPlanWhenTerminalZoneMatches() {
        // Stickiness: the squad already has an in-flight plan terminating
        // at the assigned zone. customPlan must hand back the same instance
        // (preserving currentIndex), not re-synthesize from scratch.
        // Without this gate the periodic 2-second replan would oscillate
        // the squad in and out of a building when the BFS start zone
        // flip-flops with leader/centroid movement across portals.
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int rightZone = sim.getZoneGraph().zoneIdAt(8, 3);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, rightZone);

        SquadPlan first = ClearAssignedZoneGoal.INSTANCE.customPlan(squad, sim);
        assertNotNull(first);
        squad.currentPlan = first;
        // Pretend a member already advanced past step 0.
        first.advance();
        int beforeIndex = first.currentIndex();

        SquadPlan again = ClearAssignedZoneGoal.INSTANCE.customPlan(squad, sim);
        assertTrue(again == first, "same plan instance should be returned so currentIndex is preserved");
        assertEquals(beforeIndex, again.currentIndex(), "currentIndex must survive the re-replan");
    }

    @Test
    public void customPlanRebuildsWhenTargetZoneChanges() {
        // The opposite case: assignment was switched mid-battle. The old
        // plan's terminal zone no longer matches — we MUST re-synthesize
        // toward the new target rather than honoring stale stickiness.
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int rightZone = sim.getZoneGraph().zoneIdAt(8, 3);
        int leftZone  = sim.getZoneGraph().zoneIdAt(2, 2);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, rightZone);

        SquadPlan first = ClearAssignedZoneGoal.INSTANCE.customPlan(squad, sim);
        assertNotNull(first);
        squad.currentPlan = first;

        // Commander re-assigns to a different zone.
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, leftZone);
        SquadPlan rebuilt = ClearAssignedZoneGoal.INSTANCE.customPlan(squad, sim);
        // Squad is at (2,2) which IS leftZone → ClearZone-only plan.
        assertNotNull(rebuilt);
        assertEquals(1, rebuilt.stepCount());
        assertTrue(rebuilt.steps().get(0).action instanceof ClearZone);
        assertEquals(leftZone, ((ClearZone) rebuilt.steps().get(0).action).targetZoneId());
    }

    @Test
    public void customPlanRebuildsWhenPriorPlanComplete() {
        // Plan ran to completion (every step advanced) — stickiness must NOT
        // re-hand the dead plan; rebuild as if there was nothing.
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int rightZone = sim.getZoneGraph().zoneIdAt(8, 3);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, rightZone);

        SquadPlan first = ClearAssignedZoneGoal.INSTANCE.customPlan(squad, sim);
        assertNotNull(first);
        squad.currentPlan = first;
        for (int i = 0; i < first.stepCount(); i++) first.advance();
        assertTrue(first.isComplete(), "test prerequisite: plan should be complete after advancing past last step");

        SquadPlan rebuilt = ClearAssignedZoneGoal.INSTANCE.customPlan(squad, sim);
        assertNotNull(rebuilt);
        assertTrue(rebuilt != first, "completed plan must be replaced, not reused");
    }

    @Test
    public void customPlanReturnsNullWithoutAssignment() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        // No assignedObjective — customPlan should refuse to synthesize.
        assertNull(ClearAssignedZoneGoal.INSTANCE.customPlan(squad, sim),
                "missing assignment → customPlan returns null");
    }

    @Test
    public void relevanceZeroWhenMoraleBroken() {
        // A broken squad pulls back; the commander hint defers to
        // SurviveContact. Without this yield, MISSION-priority
        // ClearAssignedZoneGoal would force the squad to keep pushing into
        // contact while morale is 0, with no recovery possible while in
        // engagement (see playtest dump squad_71 — flipping between
        // SurviveContact and ClearAssignedZone, never recovering).
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int rightZone = sim.getZoneGraph().zoneIdAt(8, 3);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, rightZone);
        // Defender in target zone — without the morale gate this would be
        // a positive-relevance pull.
        Unit defender = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 8, 3);
        sim.addUnit(defender);
        WorldState broken = WorldState.EMPTY.with(Predicate.MORALE_BROKEN, true);

        assertEquals(0f, ClearAssignedZoneGoal.INSTANCE.relevance(broken, squad, sim),
                "morale-broken squad → commander hint yields, SurviveContact takes over");
    }

    @Test
    public void priorityIsMission() {
        assertEquals(Goal.Priority.MISSION, ClearAssignedZoneGoal.INSTANCE.priority(),
                "commander-issued assignment must outrank ENGAGEMENT defaults");
    }

    @Test
    public void relevanceBelowSecureObjectiveZoneTieBreak() {
        // Both goals MISSION-priority — within a bucket Goal.pickMostRelevant
        // breaks ties on raw relevance. Confirm the commander-driven goal
        // scores lower so a planter-equipped squad keeps its unit-level target
        // (SecureObjectiveZone returns 1.0; this goal returns 0.8).
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int rightZone = sim.getZoneGraph().zoneIdAt(8, 3);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, rightZone);
        Unit defender = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 8, 3);
        sim.addUnit(defender);

        float r = ClearAssignedZoneGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim);
        assertTrue(r > 0f && r < 1.0f,
                "expected 0 < relevance < 1.0 so SecureObjectiveZone (1.0) wins the tie");
    }
}
