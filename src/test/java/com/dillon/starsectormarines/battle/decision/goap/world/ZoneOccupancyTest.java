package com.dillon.starsectormarines.battle.decision.goap.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The zone table answers "no hostile here" only for the tick and zone graph it was built for, and
 * only when it counted none; everything else is left to the live scan.
 */
class ZoneOccupancyTest {

    private static final int TICK = 7;

    /** Two 4x4 rooms either side of a solid wall at column 4. */
    private static NavigationGrid twoRooms() {
        NavigationGrid grid = new NavigationGrid(9, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 9; x++) {
                if (x != 4) grid.setWalkableFloor(x, y);
            }
        }
        return grid;
    }

    private static ZoneGraph built(NavigationGrid grid) {
        ZoneGraph graph = new ZoneGraph(grid);
        graph.rebuild();
        return graph;
    }

    @Test
    void aZoneWithNoHostileCountedIsClear() {
        ZoneGraph graph = built(twoRooms());
        int left = graph.zoneIdAt(1, 1);
        ZoneOccupancy table = new ZoneOccupancy();
        table.begin(graph);
        table.add(Faction.MARINE, left);
        table.publish(TICK);

        assertTrue(table.noHostileIn(graph, TICK, left, Faction.MARINE),
                "a unit of the asker's own side is not a hostile");
    }

    @Test
    void aHostileCountedInTheZoneDeclines() {
        ZoneGraph graph = built(twoRooms());
        int left = graph.zoneIdAt(1, 1);
        int right = graph.zoneIdAt(7, 1);
        ZoneOccupancy table = new ZoneOccupancy();
        table.begin(graph);
        table.add(Faction.DEFENDER, left);
        table.publish(TICK);

        assertFalse(table.noHostileIn(graph, TICK, left, Faction.MARINE));
        assertTrue(table.noHostileIn(graph, TICK, right, Faction.MARINE),
                "a hostile in the other room does not hold this one");
        assertTrue(table.noHostileIn(graph, TICK, left, Faction.DEFENDER),
                "the defenders' own room is clear of hostiles to them");
    }

    @Test
    void aTableOnlyAnswersForItsOwnTick() {
        ZoneGraph graph = built(twoRooms());
        int left = graph.zoneIdAt(1, 1);
        ZoneOccupancy table = new ZoneOccupancy();
        table.begin(graph);
        assertFalse(table.noHostileIn(graph, TICK, left, Faction.MARINE), "not published yet");
        table.publish(TICK);

        assertTrue(table.noHostileIn(graph, TICK, left, Faction.MARINE));
        assertFalse(table.noHostileIn(graph, TICK + 1, left, Faction.MARINE));
        assertFalse(table.noHostileIn(graph, TICK - 1, left, Faction.MARINE));
    }

    @Test
    void closingTheTableStopsItAnswering() {
        ZoneGraph graph = built(twoRooms());
        int left = graph.zoneIdAt(1, 1);
        ZoneOccupancy table = new ZoneOccupancy();
        table.begin(graph);
        table.publish(TICK);
        table.close();

        assertFalse(table.noHostileIn(graph, TICK, left, Faction.MARINE));
    }

    @Test
    void aWallBreachRetiresTheTable() {
        NavigationGrid grid = twoRooms();
        ZoneGraph graph = built(grid);
        int left = graph.zoneIdAt(1, 1);
        ZoneOccupancy table = new ZoneOccupancy();
        table.begin(graph);
        table.publish(TICK);
        assertTrue(table.noHostileIn(graph, TICK, left, Faction.MARINE));

        int before = graph.revision();
        grid.setWalkableFloor(4, 1);
        graph.applyCellsOpened(new int[]{grid.index(4, 1)});

        assertNotEquals(before, graph.revision(), "the merge is a new partition");
        assertFalse(table.noHostileIn(graph, TICK, left, Faction.MARINE),
                "rooms joined after the count, so the count no longer describes the zone");
    }

    @Test
    void aDifferentGraphIsNotAnswered() {
        NavigationGrid grid = twoRooms();
        ZoneGraph counted = built(grid);
        ZoneGraph other = built(grid);
        ZoneOccupancy table = new ZoneOccupancy();
        table.begin(counted);
        table.publish(TICK);

        assertFalse(table.noHostileIn(other, TICK, other.zoneIdAt(1, 1), Faction.MARINE));
    }

    @Test
    void beginningAgainClearsTheLastCount() {
        ZoneGraph graph = built(twoRooms());
        int left = graph.zoneIdAt(1, 1);
        ZoneOccupancy table = new ZoneOccupancy();
        table.begin(graph);
        table.add(Faction.DEFENDER, left);
        table.publish(TICK);
        assertFalse(table.noHostileIn(graph, TICK, left, Faction.MARINE));

        table.begin(graph);
        table.publish(TICK + 1);
        assertTrue(table.noHostileIn(graph, TICK + 1, left, Faction.MARINE));
    }

    /** A sim built but never ticked, so its own table stays unpublished until a test publishes it. */
    private static BattleSimulation openFloorSim() {
        NavigationGrid grid = new NavigationGrid(12, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(12, 8));
    }

    @Test
    void theQueryTrustsAClearTableButScansWhenTheTableSawAHostile() {
        BattleSimulation sim = openFloorSim();
        int zone = sim.getZoneGraph().zoneIdAt(2, 2);
        ZoneOccupancy table = sim.zoneOccupancy();

        // The table is published for the tick before a defender walks in: its clear stands.
        table.begin(sim.getZoneGraph());
        table.publish(sim.getSimTickIndex());
        sim.spawn(new EntitySpec("d1", Faction.DEFENDER, UnitType.MILITIA, 10, 2));
        assertTrue(ZoneQueries.zoneClearOfHostiles(zone, Faction.MARINE, sim),
                "the table was built before the defender arrived, and says so");
        assertTrue(ZoneQueries.areaClearOfHostiles(zone, null, Faction.MARINE, sim));

        // Once units have moved the table is closed and the live scan finds the defender.
        table.close();
        assertFalse(ZoneQueries.zoneClearOfHostiles(zone, Faction.MARINE, sim));
        assertFalse(ZoneQueries.areaClearOfHostiles(zone, null, Faction.MARINE, sim));
    }

    @Test
    void aHostileTheTableCountedIsConfirmedByTheLiveScanNotTrustedBlindly() {
        BattleSimulation sim = openFloorSim();
        int zone = sim.getZoneGraph().zoneIdAt(2, 2);
        ZoneOccupancy table = sim.zoneOccupancy();

        // The table counts a defender in the zone, but none is actually there any more.
        table.begin(sim.getZoneGraph());
        table.add(Faction.DEFENDER, zone);
        table.publish(sim.getSimTickIndex());

        assertTrue(ZoneQueries.zoneClearOfHostiles(zone, Faction.MARINE, sim),
                "the scan, not the table, decides once a hostile is counted");
    }
}
