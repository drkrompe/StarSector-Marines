package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.HoldPortalCordon;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Coverage for {@link CordonForPlant} on synthetic grids — verifies the
 * relevance gating (planter zone == squad zone, planter alive, zone has
 * portals) and the customPlan synthesis (one HoldPortalCordon step carrying
 * one GuardPost per portal).
 */
public class CordonForPlantTest {

    private static final int W = 12;
    private static final int H = 12;

    /**
     * 12x12 grid with a 5x5 "room" (zone) centered around col 6 row 6. Walls
     * frame the room at x=3, x=9, y=3, y=9. Two doorways punch through the
     * walls — one at (6, 3) and one at (3, 6). Outside the room is one
     * walkable corridor zone.
     */
    private static BattleSimulation roomWithTwoDoorways() {
        NavigationGrid grid = new NavigationGrid(W, H);
        // Floor everywhere first.
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        // Stamp room walls — perimeter at x∈{3,9} (y in 3..9) and y∈{3,9} (x in 3..9).
        for (int y = 3; y <= 9; y++) {
            grid.setWalkable(3, y, false);
            grid.setWalkable(9, y, false);
        }
        for (int x = 3; x <= 9; x++) {
            grid.setWalkable(x, 3, false);
            grid.setWalkable(x, 9, false);
        }
        // Two doorways: (6, 3) top wall, (3, 6) left wall.
        grid.setWalkableFloor(6, 3);
        grid.setDoorway(6, 3, true);
        grid.setWalkableFloor(3, 6);
        grid.setDoorway(3, 6, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad marineSquadAt(int id, float cx, float cy, int alive) {
        Squad squad = new Squad(id, Faction.MARINE);
        squad.aliveMembers = alive;
        squad.centroidX = cx;
        squad.centroidY = cy;
        return squad;
    }

    @Test
    public void priorityIsMission() {
        assertEquals(Goal.Priority.MISSION, CordonForPlant.INSTANCE.priority());
    }

    @Test
    public void relevanceZeroWhenSquadNotInPlanterZone() {
        BattleSimulation sim = roomWithTwoDoorways();
        // Charge site inside the room.
        ChargeSiteObjective charge = new ChargeSiteObjective(6, 6, 5f, "lab");
        sim.addObjective(charge);
        // Squad centroid outside the room (corridor zone).
        Squad squad = marineSquadAt(1, 1f, 1f, 2);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 1, 1);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        sim.addUnit(planter);

        assertEquals(0f, CordonForPlant.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "squad outside planter zone → CordonForPlant inactive (SecureObjectiveZone handles approach)");
    }

    @Test
    public void relevancePositiveWhenSquadInPlanterZoneWithPortals() {
        BattleSimulation sim = roomWithTwoDoorways();
        ChargeSiteObjective charge = new ChargeSiteObjective(6, 6, 5f, "lab");
        sim.addObjective(charge);

        int roomZone = sim.getZoneGraph().zoneIdAt(6, 6);
        int corridorZone = sim.getZoneGraph().zoneIdAt(1, 1);
        assertNotEquals(roomZone, corridorZone, "test prerequisite: room and corridor must be distinct zones");
        assertTrue(roomZone >= 0);

        // Place a planter inside the room — assignedObjective is the charge
        // we just added. Centroid is also inside (the cached aggregate is
        // normally refreshed by BattleSimulation; tests set it directly).
        Squad squad = marineSquadAt(1, 6f, 6f, 2);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 5, 6);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        sim.addUnit(planter);

        assertTrue(CordonForPlant.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f,
                "squad in planter zone with portals → cordon goal fires");
    }

    @Test
    public void customPlanSynthesizesOneStepWithOneGuardPostPerPortal() {
        BattleSimulation sim = roomWithTwoDoorways();
        ChargeSiteObjective charge = new ChargeSiteObjective(6, 6, 5f, "lab");
        sim.addObjective(charge);

        Squad squad = marineSquadAt(1, 6f, 6f, 2);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 5, 6);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        sim.addUnit(planter);

        SquadPlan plan = CordonForPlant.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan, "cordon goal should produce a non-null plan");
        assertEquals(1, plan.stepCount(), "cordon is a single squad-level step");

        SquadPlan.Step step = plan.steps().get(0);
        assertTrue(step.action instanceof HoldPortalCordon, "step must be a HoldPortalCordon");
        HoldPortalCordon cordon = (HoldPortalCordon) step.action;
        // Room has two doorways → two guard posts.
        assertEquals(2, cordon.posts().size(), "one guard post per portal of the planter zone");

        // Guard cells should sit inside the room (planter zone), not on the
        // doorway cells themselves — that's the cordon discipline: face out
        // from inside the room.
        int roomZone = sim.getZoneGraph().zoneIdAt(6, 6);
        for (HoldPortalCordon.GuardPost p : cordon.posts()) {
            int gz = sim.getZoneGraph().zoneIdAt(p.cellX, p.cellY);
            assertEquals(roomZone, gz, "guard cell should be inside the planter zone");
        }
    }

    @Test
    public void customPlanReturnsNullWhenNoPlanter() {
        BattleSimulation sim = roomWithTwoDoorways();
        Squad squad = marineSquadAt(1, 6f, 6f, 2);
        Unit combatant = new Unit("c1", Faction.MARINE, UnitType.MARINE, 6, 6);
        combatant.squadId = 1;
        sim.addUnit(combatant);

        assertNull(CordonForPlant.INSTANCE.customPlan(squad, sim),
                "no planter in squad → no cordon plan (squad falls back to EliminateEnemies)");
        assertEquals(0f, CordonForPlant.INSTANCE.relevance(WorldState.EMPTY, squad, sim));
    }

    @Test
    public void deadPlanterCancelsCordon() {
        BattleSimulation sim = roomWithTwoDoorways();
        ChargeSiteObjective charge = new ChargeSiteObjective(6, 6, 5f, "lab");
        sim.addObjective(charge);

        Squad squad = marineSquadAt(1, 6f, 6f, 1);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 5, 6);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        sim.addUnit(planter);
        TestUnits.kill(sim, planter);

        assertEquals(0f, CordonForPlant.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "dead planter → cordon goal inactive even when squad is in the right zone");
    }

    @Test
    public void completedChargeCancelsCordon() {
        BattleSimulation sim = roomWithTwoDoorways();
        ChargeSiteObjective charge = new ChargeSiteObjective(6, 6, 5f, "lab");
        sim.addObjective(charge);

        Squad squad = marineSquadAt(1, 6f, 6f, 1);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 6, 6);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        planter.setMoveProgress(0f);
        sim.addUnit(planter);
        // Tick the objective enough to flip isComplete().
        for (int i = 0; i < 200; i++) charge.tick(sim);
        assertTrue(charge.isComplete(), "test prerequisite: charge completes after dwell");

        assertEquals(0f, CordonForPlant.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "completed charge → cordon goal inactive; planter demotes and the squad replans to EliminateEnemies");
    }

    @Test
    public void slotsArePlanterPlusOnePerPortal() {
        BattleSimulation sim = roomWithTwoDoorways();
        ChargeSiteObjective charge = new ChargeSiteObjective(6, 6, 5f, "lab");
        sim.addObjective(charge);

        Squad squad = marineSquadAt(1, 6f, 6f, 2);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 5, 6);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        sim.addUnit(planter);

        SquadPlan plan = CordonForPlant.INSTANCE.customPlan(squad, sim);
        HoldPortalCordon cordon = (HoldPortalCordon) plan.steps().get(0).action;
        var slots = cordon.roles(squad, sim);
        // One planter slot + one slot per portal (two doorways here) = 3.
        assertEquals(3, slots.size(), "planter + two portals → three slots");
        assertEquals(HoldPortalCordon.PLANTER_SLOT, slots.get(0).name(),
                "planter slot must come first so RoleAssigner picks it before portal slots compete for the planter");
        int portalCount = 0;
        for (var slot : slots) {
            assertEquals(1, slot.count(), "each slot holds one member");
            if (slot.name().startsWith("portal:")) portalCount++;
        }
        assertEquals(2, portalCount, "two portals → two portal slots");
    }

    @Test
    public void customPlanCarriesChargeCellForPlanterSlot() {
        BattleSimulation sim = roomWithTwoDoorways();
        ChargeSiteObjective charge = new ChargeSiteObjective(6, 6, 5f, "lab");
        sim.addObjective(charge);

        Squad squad = marineSquadAt(1, 6f, 6f, 2);
        Unit planter = new Unit("p1", Faction.MARINE, UnitType.MARINE, 5, 6);
        planter.squadId = 1;
        planter.role = UnitRole.PLANTER;
        planter.assignedObjective = charge;
        sim.addUnit(planter);

        SquadPlan plan = CordonForPlant.INSTANCE.customPlan(squad, sim);
        HoldPortalCordon cordon = (HoldPortalCordon) plan.steps().get(0).action;
        assertEquals(6, cordon.chargeCellX(), "planter-slot path target must be the charge cell X");
        assertEquals(6, cordon.chargeCellY(), "planter-slot path target must be the charge cell Y");
    }
}
