package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.nav.mesh.GreedyNavigationMesh;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchicalPathfinderTest {

    @Test
    void portalCorridorRefinesAgainstFarFewerNavigableCells() {
        NavigationGrid grid = branchedThreeRoomMap();
        HierarchicalPathfinder pathfinder = pathfinder(grid);

        HierarchicalPathfinder.SearchResult result =
                pathfinder.findPathDetailed(2, 5, 25, 7,
                        true, null, null, null);

        assertEquals(HierarchicalPathfinder.SearchMode.CORRIDOR,
                result.mode());
        assertTrue(result.usedCorridor());
        assertFalse(result.fellBack());
        assertTrue(result.coarseRegionCount() >= 5);
        assertTrue(result.corridorCellCount()
                < result.navigableCellCount() * 3 / 4);
        assertCardinalPathLegal(grid, result.path(), 2, 5, 25, 7);

        int[] exact = GridPathfinder.findPath(grid, 2, 5, 25, 7,
                true, null);
        assertTrue(cardinalCost(result.path())
                <= cardinalCost(exact)
                * HierarchicalPathfinder.MAX_ACCEPTED_STRETCH);
    }

    @Test
    void excessiveCoarseDetourFallsBackToExactGridAStar() {
        NavigationGrid grid = longDetourMap();
        HierarchicalPathfinder pathfinder = pathfinder(grid);

        HierarchicalPathfinder.SearchResult result =
                pathfinder.findPathDetailed(1, 1, 10, 1,
                        true, null, null, null);
        int[] exact = GridPathfinder.findPath(grid, 1, 1, 10, 1,
                true, null);

        assertEquals(HierarchicalPathfinder.SearchMode.STRETCH_LIMIT,
                result.mode());
        assertTrue(result.fellBack());
        assertArrayEquals(exact, result.path());
        assertCardinalPathLegal(grid, result.path(), 1, 1, 10, 1);
    }

    @Test
    void staleClosedMeshFallsBackAfterBarrierDestructionThenUsesRebuild() {
        NavigationGrid grid = new NavigationGrid(12, 9);
        carveRect(grid, 1, 2, 10, 2);
        grid.setWalkableFloor(2, 3);
        grid.setDoorway(2, 3, true);
        carveRect(grid, 1, 4, 4, 7);
        grid.blockSharedEdge(5, 2, Direction.E);
        GreedyNavigationMesh mesh = new GreedyNavigationMesh(grid);
        HierarchicalPathfinder pathfinder =
                new HierarchicalPathfinder(grid, mesh);

        grid.openSharedEdge(5, 2, Direction.E);

        HierarchicalPathfinder.SearchResult stale =
                pathfinder.findPathDetailed(1, 2, 10, 2,
                        true, null, null, null);
        assertEquals(HierarchicalPathfinder.SearchMode.NO_COARSE_ROUTE,
                stale.mode());
        assertCardinalPathLegal(grid, stale.path(), 1, 2, 10, 2);

        mesh.rebuild();
        HierarchicalPathfinder.SearchResult rebuilt =
                pathfinder.findPathDetailed(1, 2, 10, 2,
                        true, null, null, null);
        assertEquals(HierarchicalPathfinder.SearchMode.CORRIDOR,
                rebuilt.mode());
        assertTrue(rebuilt.meshRevision() > stale.meshRevision());
        assertCardinalPathLegal(grid, rebuilt.path(), 1, 2, 10, 2);
    }

    @Test
    void serviceEntryPointTracksBarrierFlushAndReturnsLegalRoutes() {
        NavigationGrid grid = new NavigationGrid(12, 9);
        carveRect(grid, 1, 2, 10, 2);
        grid.setWalkableFloor(2, 3);
        grid.setDoorway(2, 3, true);
        carveRect(grid, 1, 4, 4, 7);
        grid.blockSharedEdge(5, 2, Direction.E);
        NavigationService navigation = new NavigationService(grid,
                new CellTopology(12, 9));

        assertTrue(Paths.isEmpty(navigation.findPath(1, 2, 10, 2)));

        navigation.openSharedEdge(5, 2, Direction.E);
        int[] beforeFlush = navigation.findPath(1, 2, 10, 2);
        assertCardinalOrDiagonalPathLegal(grid, beforeFlush,
                1, 2, 10, 2);

        navigation.flushNavigationTopologyIfDirty();
        int[] afterFlush = navigation.findPath(1, 2, 10, 2);
        assertCardinalOrDiagonalPathLegal(grid, afterFlush,
                1, 2, 10, 2);
    }

    @Test
    void occupancyInflationBeyondBoundUsesExactFallback() {
        NavigationGrid grid = branchedThreeRoomMap();
        byte[] occupancy = new byte[grid.getWidth() * grid.getHeight()];
        for (int x = 2; x <= 25; x++) {
            if (grid.isWalkable(x, 5)) {
                occupancy[grid.index(x, 5)] = 4;
            }
        }
        HierarchicalPathfinder pathfinder = pathfinder(grid);

        HierarchicalPathfinder.SearchResult result =
                pathfinder.findPathDetailed(2, 5, 25, 7,
                        true, occupancy, null, null);
        int[] exact = GridPathfinder.findPath(grid, 2, 5, 25, 7,
                true, occupancy);

        assertTrue(result.fellBack());
        assertArrayEquals(exact, result.path());
        assertCardinalPathLegal(grid, result.path(), 2, 5, 25, 7);
    }

    private static NavigationGrid branchedThreeRoomMap() {
        NavigationGrid grid = new NavigationGrid(30, 23);
        carveRect(grid, 1, 3, 8, 10);
        carveRect(grid, 10, 3, 17, 10);
        carveRect(grid, 19, 3, 26, 10);
        doorway(grid, 9, 5);
        doorway(grid, 18, 7);

        carveRect(grid, 1, 12, 8, 20);
        carveRect(grid, 10, 12, 17, 20);
        carveRect(grid, 19, 12, 26, 20);
        doorway(grid, 4, 11);
        doorway(grid, 13, 11);
        doorway(grid, 22, 11);
        return grid;
    }

    private static NavigationGrid longDetourMap() {
        NavigationGrid grid = new NavigationGrid(20, 23);
        carveRect(grid, 1, 1, 3, 1);
        carveRect(grid, 3, 1, 3, 10);
        carveRect(grid, 3, 10, 10, 10);
        carveRect(grid, 10, 1, 10, 10);
        doorway(grid, 5, 11);
        carveRect(grid, 1, 12, 12, 21);
        return grid;
    }

    private static HierarchicalPathfinder pathfinder(NavigationGrid grid) {
        return new HierarchicalPathfinder(grid,
                new GreedyNavigationMesh(grid));
    }

    private static void doorway(NavigationGrid grid, int x, int y) {
        grid.setWalkableFloor(x, y);
        grid.setDoorway(x, y, true);
    }

    private static void carveRect(NavigationGrid grid,
                                  int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
    }

    private static int cardinalCost(int[] path) {
        return Math.max(0, Paths.cellCount(path) - 1);
    }

    private static void assertCardinalPathLegal(
            NavigationGrid grid, int[] path,
            int startX, int startY, int goalX, int goalY) {
        assertFalse(Paths.isEmpty(path));
        assertEquals(startX, Paths.cellX(path, 0));
        assertEquals(startY, Paths.cellY(path, 0));
        assertEquals(goalX, Paths.destX(path));
        assertEquals(goalY, Paths.destY(path));
        for (int cell = 1; cell < Paths.cellCount(path); cell++) {
            int fromX = Paths.cellX(path, cell - 1);
            int fromY = Paths.cellY(path, cell - 1);
            int toX = Paths.cellX(path, cell);
            int toY = Paths.cellY(path, cell);
            Direction direction = cardinalDirection(toX - fromX, toY - fromY);
            assertTrue(grid.isWalkable(toX, toY));
            assertTrue(grid.isSharedEdgePassable(fromX, fromY, direction));
        }
    }

    private static void assertCardinalOrDiagonalPathLegal(
            NavigationGrid grid, int[] path,
            int startX, int startY, int goalX, int goalY) {
        assertFalse(Paths.isEmpty(path));
        assertEquals(startX, Paths.cellX(path, 0));
        assertEquals(startY, Paths.cellY(path, 0));
        assertEquals(goalX, Paths.destX(path));
        assertEquals(goalY, Paths.destY(path));
        for (int cell = 1; cell < Paths.cellCount(path); cell++) {
            int fromX = Paths.cellX(path, cell - 1);
            int fromY = Paths.cellY(path, cell - 1);
            int toX = Paths.cellX(path, cell);
            int toY = Paths.cellY(path, cell);
            int dx = toX - fromX;
            int dy = toY - fromY;
            assertTrue(Math.abs(dx) <= 1 && Math.abs(dy) <= 1
                    && (dx != 0 || dy != 0));
            assertTrue(grid.isWalkable(toX, toY));
            if (dx == 0 || dy == 0) {
                assertTrue(grid.isSharedEdgePassable(fromX, fromY,
                        cardinalDirection(dx, dy)));
            }
        }
    }

    private static Direction cardinalDirection(int dx, int dy) {
        if (dx == 1 && dy == 0) return Direction.E;
        if (dx == -1 && dy == 0) return Direction.W;
        if (dx == 0 && dy == 1) return Direction.N;
        if (dx == 0 && dy == -1) return Direction.S;
        throw new AssertionError("non-cardinal step: " + dx + "," + dy);
    }
}
