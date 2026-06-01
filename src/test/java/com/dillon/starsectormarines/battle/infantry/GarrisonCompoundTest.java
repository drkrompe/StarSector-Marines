package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    // ---- Defender base garrison (shared behavior, primary-node coordination) ----

    /**
     * A two-room building (Room A x25..39 y0..4, Room B x25..39 y6..9, split by
     * a wall at y=5 with a doorway, reached from outdoors via a doorway at
     * (24,2)) plus a large outdoor flood. A defender garrison node anchored in
     * either room has a ≥2-zone footprint when its compound bounds span the
     * whole building.
     */
    private static BattleSimulation twoRoomBuildingSim() {
        int w = 40, h = 10;
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x == 24) continue;
                if (x >= 25 && y == 5) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(24, 2);
        grid.setDoorway(24, 2, true);
        grid.setWalkableFloor(32, 5);
        grid.setDoorway(32, 5, true);
        return new BattleSimulation(grid, new CellTopology(w, h));
    }

    private static TacticalNode buildingNode(TacticalNode.Kind kind, int anchorX, int anchorY, int priority) {
        TacticalNode n = new TacticalNode(kind, anchorX, anchorY,
                anchorX, anchorY, anchorX, anchorY, Faction.DEFENDER, priority, 4);
        n.setCompoundBounds(25, 0, 39, 9); // footprint spans the whole building
        return n;
    }

    private static Squad defenderGarrison(int id, TacticalNode post) {
        Squad squad = new Squad(id, Faction.DEFENDER);
        squad.aliveMembers = 1;
        squad.centroidX = post.anchorX;
        squad.centroidY = post.anchorY;
        squad.holdsFireUntilKillZone = true;
        squad.assignedNode = post;
        return squad;
    }

    @Test
    public void primaryDefenderPostRunsTheAreaPatrolAndYieldsGuardPost() {
        BattleSimulation sim = twoRoomBuildingSim();
        TacticalNode command = buildingNode(TacticalNode.Kind.COMMAND_POST, 30, 2, 95);
        TacticalNode barracks = buildingNode(TacticalNode.Kind.BARRACKS, 30, 8, 60);
        sim.setTacticalMap(new TacticalMap(List.of(command, barracks)));

        Squad squad = defenderGarrison(1, command); // highest priority → primary

        assertEquals(0.95f, GarrisonCompound.INSTANCE.relevance(WorldState.EMPTY, squad, sim), 1e-6,
                "primary node of a multi-building compound runs the area patrol");
        assertEquals(0f, GuardPost.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "GuardPost yields to the area patrol for the primary node");
    }

    @Test
    public void nonPrimaryDefenderPostStaysOnGuardPost() {
        BattleSimulation sim = twoRoomBuildingSim();
        TacticalNode command = buildingNode(TacticalNode.Kind.COMMAND_POST, 30, 2, 95);
        TacticalNode barracks = buildingNode(TacticalNode.Kind.BARRACKS, 30, 8, 60);
        sim.setTacticalMap(new TacticalMap(List.of(command, barracks)));

        Squad squad = defenderGarrison(2, barracks); // lower priority → not primary

        assertEquals(0f, GarrisonCompound.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "a non-primary compound node doesn't double up on the area patrol");
        assertEquals(1.0f, GuardPost.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "the non-primary node keeps holding its own building via GuardPost");
    }

    @Test
    public void singleBuildingDefenderStaysOnGuardPost() {
        // Footprint = the node's own one-room bbox → single garrison zone →
        // not an area patrol; GuardPost handles it (unchanged behavior).
        BattleSimulation sim = twoRoomBuildingSim();
        TacticalNode lone = new TacticalNode(TacticalNode.Kind.GUARDPOST, 30, 2,
                25, 0, 39, 4, Faction.DEFENDER, 50, 4); // own bbox = Room A only, no compound widen
        sim.setTacticalMap(new TacticalMap(List.of(lone)));
        Squad squad = defenderGarrison(3, lone);

        assertEquals(0f, GarrisonCompound.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "single-zone garrison isn't an area patrol");
        assertEquals(1.0f, GuardPost.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "single-building post stays on GuardPost");
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
