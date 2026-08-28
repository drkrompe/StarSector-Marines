package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationEdgeBarrierTest {

    @Test
    void sharedMutationKeepsReciprocalHalvesInLockstep() {
        NavigationGrid grid = walkableGrid(2, 1);

        grid.blockSharedEdge(0, 0, Direction.E);

        assertFalse(grid.isEdgePassable(0, 0, Direction.E));
        assertFalse(grid.isEdgePassable(1, 0, Direction.W));
        assertFalse(grid.isSharedEdgePassable(0, 0, Direction.E));

        grid.openSharedEdge(1, 0, Direction.W);

        assertTrue(grid.isEdgePassable(0, 0, Direction.E));
        assertTrue(grid.isEdgePassable(1, 0, Direction.W));
        assertTrue(grid.isSharedEdgePassable(0, 0, Direction.E));
    }

    @Test
    void sharedMutationRejectsDiagonalCorners() {
        NavigationGrid grid = walkableGrid(2, 2);

        assertThrows(IllegalArgumentException.class,
                () -> grid.blockSharedEdge(0, 0, Direction.NE));
    }

    @Test
    void cardinalPathRoutesAroundClosedSharedEdge() {
        NavigationGrid grid = walkableGrid(2, 2);
        grid.blockSharedEdge(0, 0, Direction.E);

        int[] path = GridPathfinder.findPath(grid, 0, 0, 1, 0,
                true, null);

        assertArrayEquals(new int[]{0, 0, 0, 1, 1, 1, 1, 0}, path);
    }

    @Test
    void diagonalPathCannotCutAcrossBarrierEndpoint() {
        NavigationGrid grid = walkableGrid(2, 2);
        grid.blockSharedEdge(0, 0, Direction.E);

        int[] path = GridPathfinder.findPath(grid, 0, 0, 1, 1,
                false, null);

        assertArrayEquals(new int[]{0, 0, 0, 1, 1, 1}, path);
    }

    @Test
    void closedSharedEdgeSplitsOtherwiseWalkableZones() {
        NavigationGrid grid = walkableGrid(3, 1);
        grid.blockSharedEdge(1, 0, Direction.E);
        ZoneGraph graph = new ZoneGraph(grid);
        graph.rebuild();

        int left = graph.zoneIdAt(0, 0);
        int right = graph.zoneIdAt(2, 0);

        assertNotEquals(left, right);
        assertFalse(graph.areConnected(left, right));
    }

    @Test
    void closedDoorwayEdgeDoesNotPublishFalsePortal() {
        NavigationGrid grid = walkableGrid(3, 1);
        grid.setDoorway(1, 0, true);
        grid.blockSharedEdge(1, 0, Direction.E);
        ZoneGraph graph = new ZoneGraph(grid);
        graph.rebuild();

        int left = graph.zoneIdAt(0, 0);
        int right = graph.zoneIdAt(2, 0);

        assertFalse(graph.areConnected(left, right));
    }

    @Test
    void runtimeOpeningRebuildsZonesAtTopologyBoundary() {
        NavigationGrid grid = walkableGrid(3, 1);
        grid.blockSharedEdge(1, 0, Direction.E);
        NavigationService navigation = new NavigationService(grid,
                new CellTopology(3, 1));
        int left = navigation.getZoneGraph().zoneIdAt(0, 0);
        int right = navigation.getZoneGraph().zoneIdAt(2, 0);
        assertFalse(navigation.getZoneGraph().areConnected(left, right));

        navigation.openSharedEdge(1, 0, Direction.E);

        assertTrue(navigation.isZoneGraphDirty());
        navigation.flushZoneGraphIfDirty();
        assertFalse(navigation.isZoneGraphDirty());
        assertTrue(navigation.getZoneGraph().areConnected(
                navigation.getZoneGraph().zoneIdAt(0, 0),
                navigation.getZoneGraph().zoneIdAt(2, 0)));
    }

    private static NavigationGrid walkableGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return grid;
    }
}
