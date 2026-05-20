package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
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
    public void relevanceZeroWhenAlreadyInTargetZone() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int leftZone = sim.getZoneGraph().zoneIdAt(2, 2);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, leftZone);

        assertEquals(0f, ClearAssignedZoneGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "already in assigned zone → goal inactive, lower buckets pick up");
    }

    @Test
    public void relevancePositiveWhenAssignedZoneIsReachable() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        int rightZone = sim.getZoneGraph().zoneIdAt(8, 3);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, rightZone);

        assertTrue(ClearAssignedZoneGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f,
                "reachable CLEAR_ZONE assignment should make the goal relevant");
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
    public void customPlanReturnsNullWithoutAssignment() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        // No assignedObjective — customPlan should refuse to synthesize.
        assertNull(ClearAssignedZoneGoal.INSTANCE.customPlan(squad, sim),
                "missing assignment → customPlan returns null");
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

        float r = ClearAssignedZoneGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim);
        assertTrue(r > 0f && r < 1.0f,
                "expected 0 < relevance < 1.0 so SecureObjectiveZone (1.0) wins the tie");
    }
}
