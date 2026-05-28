package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.ChokePointHold;
import com.dillon.starsectormarines.battle.ai.goap.actions.GarrisonCordon;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link GarrisonAmbush} — verifies the relevance gating
 * (garrison-routed defender squad in a zone with portals + visible enemy) and
 * the customPlan branching (single portal → ChokePointHold, multi-portal →
 * GarrisonCordon).
 */
public class GarrisonAmbushTest {

    private static final int W = 14;
    private static final int H = 14;

    /**
     * 14x14 grid with a 7x7 room walled off (perimeter at x∈{3,9}, y∈{3,9})
     * and a SINGLE doorway at (6, 3). Inside the room is one zone; outside
     * the room is the corridor zone.
     */
    private static BattleSimulation singlePortalRoom() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        for (int y = 3; y <= 9; y++) {
            grid.setWalkable(3, y, false);
            grid.setWalkable(9, y, false);
        }
        for (int x = 3; x <= 9; x++) {
            grid.setWalkable(x, 3, false);
            grid.setWalkable(x, 9, false);
        }
        // Single doorway in the top wall.
        grid.setWalkableFloor(6, 3);
        grid.setDoorway(6, 3, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Same 14x14 room but with TWO doorways — one at (6,3) and one at (3,6). */
    private static BattleSimulation multiPortalRoom() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        for (int y = 3; y <= 9; y++) {
            grid.setWalkable(3, y, false);
            grid.setWalkable(9, y, false);
        }
        for (int x = 3; x <= 9; x++) {
            grid.setWalkable(x, 3, false);
            grid.setWalkable(x, 9, false);
        }
        grid.setWalkableFloor(6, 3);
        grid.setDoorway(6, 3, true);
        grid.setWalkableFloor(3, 6);
        grid.setDoorway(3, 6, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Defender squad in garrison posture — flag matches what BattleSetup stamps for GARRISON-routed squads. */
    private static Squad garrisonSquadAt(int id, float centroidX, float centroidY, int alive) {
        Squad squad = new Squad(id, Faction.DEFENDER);
        squad.aliveMembers = alive;
        squad.centroidX = centroidX;
        squad.centroidY = centroidY;
        squad.holdsFireUntilKillZone = true;
        return squad;
    }

    @Test
    public void priorityIsMission() {
        assertEquals(Goal.Priority.MISSION, GarrisonAmbush.INSTANCE.priority());
    }

    @Test
    public void relevanceZeroForNonGarrisonSquad() {
        BattleSimulation sim = singlePortalRoom();
        Squad squad = new Squad(1, Faction.DEFENDER);
        squad.aliveMembers = 2;
        squad.centroidX = 6f;
        squad.centroidY = 6f;
        // holdsFireUntilKillZone left false — marine-deboard / patrol shape.

        // Add an attacker so enemyKnown() would otherwise be true.
        sim.addUnit(new Unit("a1", Faction.MARINE, UnitType.MARINE, 1, 1));

        assertEquals(0f, GarrisonAmbush.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "no holdsFireUntilKillZone → goal inactive");
    }

    @Test
    public void relevanceZeroWhenNoEnemyOnMap() {
        BattleSimulation sim = singlePortalRoom();
        Squad squad = garrisonSquadAt(1, 6f, 6f, 2);
        // No attacker added.
        assertEquals(0f, GarrisonAmbush.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "no known enemy → garrison ambush inactive, squad falls through to idle posture");
    }

    @Test
    public void relevancePositiveForGarrisonInPortalZoneWithEnemy() {
        BattleSimulation sim = singlePortalRoom();
        Squad squad = garrisonSquadAt(1, 6f, 6f, 2);
        // Put a defender member inside the room too, so sim.getUnits() reflects
        // a realistic squad layout (the relevance check itself only consults
        // squad.holdsFireUntilKillZone + the unit list for "enemy known").
        Unit defender = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 5, 6);
        defender.squadId = squad.id;
        sim.addUnit(defender);
        sim.addUnit(new Unit("a1", Faction.MARINE, UnitType.MARINE, 1, 1));

        assertTrue(GarrisonAmbush.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f,
                "garrison-routed squad in a zone with portals + visible enemy → goal fires");
    }

    @Test
    public void customPlanEmitsChokePointHoldForSinglePortalZone() {
        BattleSimulation sim = singlePortalRoom();
        int roomZone = sim.getZoneGraph().zoneIdAt(6, 6);
        int portalCount = sim.getZoneGraph().zoneById(roomZone).getPortalIds().size();
        assertEquals(1, portalCount, "test prerequisite: single-portal room");

        Squad squad = garrisonSquadAt(1, 6f, 6f, 2);
        Unit d1 = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 5, 5);
        d1.squadId = squad.id;
        Unit d2 = new Unit("d2", Faction.DEFENDER, UnitType.MARINE, 7, 5);
        d2.squadId = squad.id;
        sim.addUnit(d1);
        sim.addUnit(d2);
        sim.addUnit(new Unit("a1", Faction.MARINE, UnitType.MARINE, 1, 1));

        SquadPlan plan = GarrisonAmbush.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan, "single-portal zone should yield a plan");
        assertEquals(1, plan.stepCount(), "garrison ambush is a single squad-level step");
        assertTrue(plan.steps().get(0).action instanceof ChokePointHold,
                "single-portal zone → action must be ChokePointHold");
        ChokePointHold hold = (ChokePointHold) plan.steps().get(0).action;
        assertEquals(6, hold.portalX(), "portal cell x must match the doorway");
        assertEquals(3, hold.portalY(), "portal cell y must match the doorway");
        assertTrue(hold.losCells().size() > 0, "the hold must carry at least one LOS cell");
    }

    @Test
    public void customPlanEmitsGarrisonCordonForMultiPortalZone() {
        BattleSimulation sim = multiPortalRoom();
        int roomZone = sim.getZoneGraph().zoneIdAt(6, 6);
        int portalCount = sim.getZoneGraph().zoneById(roomZone).getPortalIds().size();
        assertEquals(2, portalCount, "test prerequisite: multi-portal room with two doorways");

        Squad squad = garrisonSquadAt(1, 6f, 6f, 2);
        Unit d1 = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 5, 5);
        d1.squadId = squad.id;
        Unit d2 = new Unit("d2", Faction.DEFENDER, UnitType.MARINE, 7, 5);
        d2.squadId = squad.id;
        sim.addUnit(d1);
        sim.addUnit(d2);
        sim.addUnit(new Unit("a1", Faction.MARINE, UnitType.MARINE, 1, 1));

        SquadPlan plan = GarrisonAmbush.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan, "multi-portal zone should yield a plan");
        assertEquals(1, plan.stepCount(), "garrison ambush is a single squad-level step");
        assertTrue(plan.steps().get(0).action instanceof GarrisonCordon,
                "multi-portal zone → action must be GarrisonCordon");
        GarrisonCordon cordon = (GarrisonCordon) plan.steps().get(0).action;
        assertEquals(2, cordon.posts().size(),
                "one guard post per portal of the squad's zone");
        // Guard cells must sit inside the room (defender zone), not outside.
        for (var post : cordon.posts()) {
            int gz = sim.getZoneGraph().zoneIdAt(post.cellX, post.cellY);
            assertEquals(roomZone, gz, "guard cell should be inside the squad's zone");
        }
    }
}
