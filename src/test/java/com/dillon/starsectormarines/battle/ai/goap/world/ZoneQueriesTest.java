package com.dillon.starsectormarines.battle.ai.goap.world;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic-grid coverage for {@link ZoneQueries}. Each test builds a small
 * grid by hand, constructs a {@link BattleSimulation} so the embedded
 * {@link com.dillon.starsectormarines.battle.nav.zone.ZoneGraph} runs detection
 * on it, and exercises one query.
 */
public class ZoneQueriesTest {

    private static final int W = 10;
    private static final int H = 10;
    private static final int WALL_COL = 5;

    /** 10x10 grid split by a wall at column 5 with a doorway at (5, 5). Three zones total: left floor, right floor, doorway. */
    private static BattleSimulation singleDoorwaySim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == WALL_COL) continue; // wall
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(WALL_COL, 5);
        grid.setDoorway(WALL_COL, 5, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** 10x10 grid split by a wall at column 5 with two doorways at (5, 2) and (5, 7). Four zones total: left floor, right floor, two doorway zones. */
    private static BattleSimulation twoDoorwaySim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == WALL_COL) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(WALL_COL, 2);
        grid.setDoorway(WALL_COL, 2, true);
        grid.setWalkableFloor(WALL_COL, 7);
        grid.setDoorway(WALL_COL, 7, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** 10x10 grid split by a solid wall at column 5 with no doorway. Two disconnected floor zones. */
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

    @Test
    public void squadCentroidResolvesToContainingZone() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;
        squad.centroidX = 2f;
        squad.centroidY = 2f;
        int leftZone = sim.getZoneGraph().zoneIdAt(2, 2);
        assertTrue(leftZone >= 0, "left half should be a real zone");
        assertEquals(leftZone, ZoneQueries.squadCurrentZone(squad, sim));

        squad.centroidX = 8f;
        squad.centroidY = 3f;
        int rightZone = sim.getZoneGraph().zoneIdAt(8, 3);
        assertTrue(rightZone >= 0, "right half should be a real zone");
        assertNotEquals(leftZone, rightZone, "the two halves must be distinct zones");
        assertEquals(rightZone, ZoneQueries.squadCurrentZone(squad, sim));
    }

    @Test
    public void squadCurrentZoneReturnsMinusOneWhenNoMembers() {
        BattleSimulation sim = singleDoorwaySim();
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 0;
        assertEquals(-1, ZoneQueries.squadCurrentZone(squad, sim));
    }

    @Test
    public void portalsOfZoneWithTwoDoorwaysReturnsBoth() {
        BattleSimulation sim = twoDoorwaySim();
        int leftZone = sim.getZoneGraph().zoneIdAt(2, 2);
        List<Integer> portals = ZoneQueries.portalsOf(leftZone, sim);
        assertEquals(2, portals.size(), "left floor should border two doorway portals");
        // sanity: each portal id is valid + connects the left zone to a doorway zone
        for (int portalId : portals) {
            assertTrue(sim.getZoneGraph().portalById(portalId) != null);
            int other = sim.getZoneGraph().portalById(portalId).otherZone(leftZone);
            assertTrue(other >= 0, "portal must have a far side");
        }
    }

    @Test
    public void portalsOfInvalidZoneIsEmpty() {
        BattleSimulation sim = singleDoorwaySim();
        assertTrue(ZoneQueries.portalsOf(-1, sim).isEmpty());
        assertTrue(ZoneQueries.portalsOf(9999, sim).isEmpty());
    }

    @Test
    public void zoneClearFlipsWithEnemyPresence() {
        BattleSimulation sim = singleDoorwaySim();
        int rightZone = sim.getZoneGraph().zoneIdAt(8, 3);
        assertTrue(ZoneQueries.zoneClear(rightZone, Faction.DEFENDER, sim),
                "empty zone is clear by definition");

        Unit defender = new Unit("d1", Faction.DEFENDER, UnitType.MILITIA, 8, 3);
        sim.addUnit(defender);
        assertFalse(ZoneQueries.zoneClear(rightZone, Faction.DEFENDER, sim),
                "live defender in the zone should make it not-clear");

        // A defender in the *other* zone shouldn't taint the right zone.
        int leftZone = sim.getZoneGraph().zoneIdAt(2, 2);
        assertTrue(ZoneQueries.zoneClear(leftZone, Faction.DEFENDER, sim),
                "left zone has no defender so it should still read clear");

        // A marine in the right zone is irrelevant when we're asking about DEFENDERs.
        sim.addUnit(new Unit("m1", Faction.MARINE, UnitType.MARINE, 7, 3));
        assertTrue(ZoneQueries.zoneClear(rightZone, Faction.MARINE, sim) == false,
                "marine in right zone should make it not-clear for the MARINE faction");
        assertFalse(ZoneQueries.zoneClear(rightZone, Faction.DEFENDER, sim),
                "marine doesn't influence DEFENDER clarity check");

        // Kill the defender → zone reads clear again.
        defender.hp = 0f;
        assertTrue(ZoneQueries.zoneClear(rightZone, Faction.DEFENDER, sim),
                "dead defender should not count");
    }

    @Test
    public void zonePathBfsReturnsThreeZoneChain() {
        BattleSimulation sim = singleDoorwaySim();
        int leftZone    = sim.getZoneGraph().zoneIdAt(2, 2);
        int doorwayZone = sim.getZoneGraph().zoneIdAt(WALL_COL, 5);
        int rightZone   = sim.getZoneGraph().zoneIdAt(8, 3);
        assertTrue(leftZone >= 0 && doorwayZone >= 0 && rightZone >= 0);

        List<Integer> path = ZoneQueries.zonePathBfs(leftZone, rightZone, sim);
        // Three-zone A-B-C chain: left floor → doorway → right floor.
        assertEquals(List.of(leftZone, doorwayZone, rightZone), path);
    }

    @Test
    public void zonePathBfsIdentityReturnsSingleton() {
        BattleSimulation sim = singleDoorwaySim();
        int leftZone = sim.getZoneGraph().zoneIdAt(2, 2);
        assertEquals(List.of(leftZone), ZoneQueries.zonePathBfs(leftZone, leftZone, sim));
    }

    @Test
    public void zonePathBfsDisconnectedReturnsEmpty() {
        BattleSimulation sim = disconnectedSim();
        int leftZone  = sim.getZoneGraph().zoneIdAt(2, 2);
        int rightZone = sim.getZoneGraph().zoneIdAt(8, 3);
        assertTrue(leftZone >= 0 && rightZone >= 0);
        assertNotEquals(leftZone, rightZone, "no doorway means two distinct zones");
        assertTrue(ZoneQueries.zonePathBfs(leftZone, rightZone, sim).isEmpty(),
                "fully walled-off zones must report no path");
    }

    @Test
    public void zonePathBfsInvalidIdsReturnEmpty() {
        BattleSimulation sim = singleDoorwaySim();
        assertTrue(ZoneQueries.zonePathBfs(-1, 0, sim).isEmpty());
        assertTrue(ZoneQueries.zonePathBfs(0, 9999, sim).isEmpty());
    }
}
