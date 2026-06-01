package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link GarrisonCompound} — the marine {@code HOLD_NODE} consumer
 * that garrisons a captured compound. Uses the 3-zone corridor (start room,
 * large outdoor, compound room) so the node's footprint resolves to a real
 * garrison area.
 */
public class GarrisonCompoundTest {

    private static final int W = 30;
    private static final int H = 10;
    private static final int WALL_A = 4;
    private static final int WALL_B = 24;

    private static BattleSimulation threeZoneSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == WALL_A || x == WALL_B) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(WALL_A, 5);
        grid.setDoorway(WALL_A, 5, true);
        grid.setWalkableFloor(WALL_B, 5);
        grid.setDoorway(WALL_B, 5, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static TacticalNode compoundNode() {
        return new TacticalNode(TacticalNode.Kind.ARMORY, 27, 5,
                25, 0, 29, 9, Faction.DEFENDER, 80, 4);
    }

    private static Squad marineSquad() {
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;
        squad.centroidX = 27f;
        squad.centroidY = 5f;
        return squad;
    }

    @Test
    public void relevantForHoldNodeWithAGarrisonArea() {
        BattleSimulation sim = threeZoneSim();
        Squad squad = marineSquad();
        squad.assignedObjective = ObjectiveAssignment.holdNode(squad.id, compoundNode());

        assertEquals(0.95f, GarrisonCompound.INSTANCE.relevance(WorldState.EMPTY, squad, sim), 1e-6,
                "HOLD_NODE with a real garrison area → goal relevant");
        assertEquals(Goal.Priority.MISSION, GarrisonCompound.INSTANCE.priority());
    }

    @Test
    public void irrelevantWithoutHoldNodeAssignment() {
        BattleSimulation sim = threeZoneSim();
        Squad squad = marineSquad();
        // No assignment, and a non-HOLD_NODE assignment, both yield 0.
        assertEquals(0f, GarrisonCompound.INSTANCE.relevance(WorldState.EMPTY, squad, sim));
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id,
                sim.getZoneGraph().zoneIdAt(27, 5));
        assertEquals(0f, GarrisonCompound.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "CLEAR_ZONE is ClearAssignedZoneGoal's job, not GarrisonCompound's");
    }

    @Test
    public void yieldsOnMoraleBreak() {
        BattleSimulation sim = threeZoneSim();
        Squad squad = marineSquad();
        squad.assignedObjective = ObjectiveAssignment.holdNode(squad.id, compoundNode());
        WorldState broken = WorldState.EMPTY.with(Predicate.MORALE_BROKEN, true);

        assertEquals(0f, GarrisonCompound.INSTANCE.relevance(broken, squad, sim),
                "morale-broken garrison yields to SurviveContact");
    }

    @Test
    public void customPlanIsASingleGarrisonPatrolStep() {
        BattleSimulation sim = threeZoneSim();
        Squad squad = marineSquad();
        squad.assignedObjective = ObjectiveAssignment.holdNode(squad.id, compoundNode());

        SquadPlan plan = GarrisonCompound.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan, "HOLD_NODE with a garrison area → a plan");
        assertEquals(1, plan.stepCount(), "garrison plan is one perpetual step");
        assertTrue(plan.steps().get(0).action instanceof GarrisonPatrol,
                "the step is a GarrisonPatrol");
    }
}
