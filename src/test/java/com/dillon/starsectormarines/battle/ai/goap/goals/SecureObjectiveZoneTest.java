package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitRole;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.ClearZone;
import com.dillon.starsectormarines.battle.ai.goap.actions.EnterZone;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.ChargeSiteObjective;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic-grid coverage for {@link SecureObjectiveZone}. Tests that the
 * goal's relevance gating + custom-plan synthesis produce the expected
 * EnterZone→ClearZone alternation across a zone-graph BFS path.
 */
public class SecureObjectiveZoneTest {

    private static final int W = 10;
    private static final int H = 10;
    private static final int WALL_COL = 5;

    /** 10x10, two halves split by a wall at column 5 with a doorway at (5, 5). Three zones: left floor, doorway cell, right floor. */
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

    /** 10x10, solid wall at column 5 — two disconnected floor zones. */
    private static BattleSimulation disconnectedSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == WALL_COL) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Build a squad with the given members; centroid is hand-set since the cached aggregates are normally refreshed by BattleSimulation.updateSquadAlertLevels. */
    private static Squad squadAt(int id, float centroidX, float centroidY, int aliveMembers) {
        Squad squad = new Squad(id, Faction.MARINE);
        squad.aliveMembers = aliveMembers;
        squad.centroidX = centroidX;
        squad.centroidY = centroidY;
        return squad;
    }

    @Test
    public void relevanceZeroWhenSquadHasNoChargeObjective() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = squadAt(1, 2f, 2f, 1);
        // Squad member exists but with no assignedObjective.
        Unit u = new Unit("m1", Faction.MARINE, UnitType.MARINE, 2, 2);
        u.squadId = 1;
        sim.addUnit(u);

        assertEquals(0f, SecureObjectiveZone.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "no charge objective → goal inactive");
    }

    @Test
    public void relevanceZeroWhenObjectiveInSameZone() {
        BattleSimulation sim = singleDoorwaySim();
        // Charge site at (3, 3) — same zone as the squad centroid at (2, 2).
        ChargeSiteObjective charge = new ChargeSiteObjective(3, 3, 5f, "test");
        sim.addObjective(charge);
        Squad squad = squadAt(1, 2f, 2f, 1);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 2, 2);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        sim.addUnit(planter);

        assertEquals(0f, SecureObjectiveZone.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "already in objective zone → goal inactive, EliminateEnemies takes over");
    }

    @Test
    public void relevanceZeroWhenObjectiveDisconnectedFromSquad() {
        BattleSimulation sim = disconnectedSim();
        // Charge site on the right half; squad on the left. No doorway.
        ChargeSiteObjective charge = new ChargeSiteObjective(8, 3, 5f, "test");
        sim.addObjective(charge);
        Squad squad = squadAt(1, 2f, 2f, 1);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 2, 2);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        sim.addUnit(planter);

        assertEquals(0f, SecureObjectiveZone.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "disconnected objective should fall through to EliminateEnemies, not strand the squad plan-less");
    }

    @Test
    public void customPlanSynthesizesEnterClearPairsAcrossBfsPath() {
        BattleSimulation sim = singleDoorwaySim();
        // Charge site at (8, 3) — right half. Squad on left half.
        ChargeSiteObjective charge = new ChargeSiteObjective(8, 3, 5f, "test");
        sim.addObjective(charge);
        Squad squad = squadAt(1, 2f, 2f, 1);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 2, 2);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        sim.addUnit(planter);

        SquadPlan plan = SecureObjectiveZone.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan, "reachable objective should produce a non-null plan");
        // BFS path is [leftZone, doorway, rightZone] (3 zones). The
        // synthesizer skips zone 0 (current zone) and emits an EnterZone +
        // ClearZone pair per remaining zone — 2 zones × 2 steps = 4 steps.
        assertEquals(4, plan.stepCount(), "expect 2 zone hops × (EnterZone, ClearZone) = 4 steps");

        int doorwayZone = sim.getZoneGraph().zoneIdAt(WALL_COL, 5);
        int rightZone   = sim.getZoneGraph().zoneIdAt(8, 3);

        // Step 0: EnterZone(doorway). Step 1: ClearZone(doorway).
        assertTrue(plan.steps().get(0).action instanceof EnterZone);
        assertEquals(doorwayZone, ((EnterZone) plan.steps().get(0).action).targetZoneId());
        assertTrue(plan.steps().get(1).action instanceof ClearZone);
        assertEquals(doorwayZone, ((ClearZone) plan.steps().get(1).action).targetZoneId());

        // Step 2: EnterZone(right). Step 3: ClearZone(right).
        assertTrue(plan.steps().get(2).action instanceof EnterZone);
        assertEquals(rightZone, ((EnterZone) plan.steps().get(2).action).targetZoneId());
        assertTrue(plan.steps().get(3).action instanceof ClearZone);
        assertEquals(rightZone, ((ClearZone) plan.steps().get(3).action).targetZoneId());
    }

    @Test
    public void customPlanReturnsNullWhenAlreadyInObjectiveZone() {
        BattleSimulation sim = singleDoorwaySim();
        ChargeSiteObjective charge = new ChargeSiteObjective(3, 3, 5f, "test");
        sim.addObjective(charge);
        Squad squad = squadAt(1, 2f, 2f, 1);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 2, 2);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        sim.addUnit(planter);

        assertNull(SecureObjectiveZone.INSTANCE.customPlan(squad, sim),
                "no zone hop needed → customPlan returns null (squad shouldn't replan into no-op steps)");
    }

    @Test
    public void priorityIsMission() {
        assertEquals(Goal.Priority.MISSION, SecureObjectiveZone.INSTANCE.priority(),
                "SecureObjectiveZone must outrank ENGAGEMENT so squads with a charge target don't dawdle on first-contact infantry");
    }

    @Test
    public void completedObjectiveStopsCountingAsRelevant() {
        BattleSimulation sim = singleDoorwaySim();
        ChargeSiteObjective charge = new ChargeSiteObjective(8, 3, 5f, "test");
        sim.addObjective(charge);
        Squad squad = squadAt(1, 2f, 2f, 1);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 2, 2);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        sim.addUnit(planter);

        // Advance objective progress past its duration so isComplete() flips true.
        for (Unit u : sim.getUnits()) {
            if (u.role == UnitRole.PLANTER) {
                u.cellX = 8;
                u.cellY = 3;
                u.moveProgress = 0f;
            }
        }
        // Tick the objective enough times to complete.
        for (int i = 0; i < 200; i++) charge.tick(sim);
        assertTrue(charge.isComplete(), "test prerequisite: objective should be complete after dwell");

        assertEquals(0f, SecureObjectiveZone.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "completed charge objective should no longer drive the goal");
    }
}
