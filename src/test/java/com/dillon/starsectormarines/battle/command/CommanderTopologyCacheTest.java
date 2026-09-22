package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommanderTopologyCacheTest {

    @Test
    void frozenZoneComponentsMatchPortalSearchForEveryZonePair() {
        NavigationGrid grid = new NavigationGrid(12, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
            grid.setWalkable(4, y, false);
            grid.setWalkable(8, y, false);
        }
        grid.setWalkableFloor(4, 3);
        grid.setDoorway(4, 3, true);
        try (BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(grid.getWidth(), grid.getHeight()))) {
            CommandTopology topology = CommandTopology.freeze(sim);
            assertTrue(topology.zones().size() >= 4);
            for (int start = 0; start < topology.zones().size(); start++) {
                for (int target = 0; target < topology.zones().size(); target++) {
                    assertEquals(portalSearch(topology, start, target),
                            topology.areZonesConnected(start, target),
                            start + " -> " + target);
                }
            }
            assertFalse(topology.areZonesConnected(-1, 0));
            assertFalse(topology.areZonesConnected(0, topology.zones().size()));
        }
    }

    private static boolean portalSearch(CommandTopology topology, int start, int target) {
        if (start == target) return true;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            for (int adjacent : topology.zone(queue.removeFirst()).adjacentZones()) {
                if (adjacent == target) return true;
                if (visited.add(adjacent)) queue.addLast(adjacent);
            }
        }
        return false;
    }

    @Test
    void immutableTopologyIsReusedUntilNavigationRevisionChanges() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
        }
        try (BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(grid.getWidth(), grid.getHeight()))) {
            CommanderService service = new CommanderService();
            CommandTopology first = service.freezeTopology(sim);
            assertSame(first, service.freezeTopology(sim));

            grid.setWalkable(4, 4, false);
            grid.setDoorway(3, 4, true);
            grid.setEdgePassable(3, 4, Direction.E, false);
            CommandTopology rebuilt = service.freezeTopology(sim);

            assertNotSame(first, rebuilt);
            assertTrue(first.isWalkable(4, 4));
            assertFalse(rebuilt.isWalkable(4, 4));
            assertTrue(rebuilt.isDoorwayCell(grid.index(3, 4)));
            NavigationGrid frozen = grid.copyNavigationTopology();
            assertFalse(frozen.isEdgePassable(3, 4, Direction.E));
            grid.setEdgePassable(3, 4, Direction.E, true);
            assertFalse(frozen.isEdgePassable(3, 4, Direction.E));
        }
    }
}
