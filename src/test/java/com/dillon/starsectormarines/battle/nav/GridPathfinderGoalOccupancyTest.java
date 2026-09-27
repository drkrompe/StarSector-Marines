package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GridPathfinderGoalOccupancyTest {
    private final String previous = System.getProperty(GridPathfinder.OMIT_FIXED_GOAL_OCCUPANCY_PROPERTY);

    @AfterEach
    void restoreProperty() {
        if (previous == null) System.clearProperty(GridPathfinder.OMIT_FIXED_GOAL_OCCUPANCY_PROPERTY);
        else System.setProperty(GridPathfinder.OMIT_FIXED_GOAL_OCCUPANCY_PROPERTY, previous);
        TickInnerProfile.releaseCurrentThread();
    }

    @Test
    void crowdedFixedGoalDoesNotFloodUnrelatedOpenGround() {
        NavigationGrid grid = openGrid(40, 24);
        byte[] occupancy = new byte[40 * 24];
        occupancy[grid.index(24, 12)] = (byte) 255;
        for (boolean cardinal : new boolean[]{true, false}) {
            TickInnerProfile control = new TickInnerProfile();
            TickInnerProfile.setCurrent(control);
            setOmission(false);
            int[] before = GridPathfinder.findPath(grid, 14, 12, 24, 12, cardinal, occupancy);
            TickInnerProfile subject = new TickInnerProfile();
            TickInnerProfile.setCurrent(subject);
            setOmission(true);
            int[] after = GridPathfinder.findPath(grid, 14, 12, 24, 12, cardinal, occupancy);
            assertArrayEquals(before, after);
            assertEquals(11, after.length / 2);
            assertEquals(960, control.pathfindExpandedNodes());
            assertEquals(11, subject.pathfindExpandedNodes());
            assertEquals(255, subject.slowPathSearches().get(0).goalOccupancy());
        }
    }

    @Test
    void denseAndIndexedCostsRemainOptimalAgainstIndependentDijkstra() {
        setOmission(true);
        for (boolean cardinal : new boolean[]{true, false}) {
            for (int seed = 0; seed < 24; seed++) {
                Random random = new Random(seed);
                NavigationGrid grid = openGrid(9, 7);
                byte[] occupancy = new byte[63];
                float[] costs = new float[63];
                boolean[] clearance = new boolean[63];
                for (int i = 0; i < 63; i++) {
                    int x = i % 9, y = i / 9;
                    costs[i] = 1f + random.nextInt(8) * .25f;
                    occupancy[i] = (byte) random.nextInt(4);
                    clearance[i] = random.nextInt(9) != 0;
                    if (random.nextInt(7) == 0) grid.setWalkable(x, y, false);
                    if (random.nextInt(5) == 0) grid.setEdgePassable(x, y, Direction.E, false);
                    if (random.nextInt(5) == 0) grid.setEdgePassable(x, y, Direction.S, false);
                }
                grid.setWalkableFloor(0, 0);
                grid.setWalkableFloor(8, 6);
                clearance[0] = clearance[62] = true;
                occupancy[62] = (byte) 255;
                // The expensive terminal terrain remains direction-sensitive.
                costs[62] = 8.75f;
                int[] dense = GridPathfinder.findPath(grid, 0, 0, 8, 6,
                        cardinal, occupancy, costs, clearance);
                assertOptimal(grid, dense, cardinal, occupancy, costs, clearance);
                RouteCostField field = new RouteCostField(costs, RouteCostField.nextRevision());
                int[] indexed = GridPathfinder.findPathWithCost(grid, 0, 0, 8, 6,
                        cardinal, occupancy, field);
                assertOptimal(grid, indexed, cardinal, occupancy, costs, null);
            }
        }
    }

    @Test
    void intermediateOccupancyStillRoutesAroundCrowding() {
        setOmission(true);
        NavigationGrid grid = openGrid(5, 3);
        byte[] occupancy = new byte[15];
        occupancy[grid.index(2, 1)] = (byte) 255;
        occupancy[grid.index(4, 1)] = (byte) 255;
        int[] path = GridPathfinder.findPath(grid, 0, 1, 4, 1, true, occupancy);
        assertEquals(7, path.length / 2);
        for (int i = 2; i < path.length - 2; i += 2) {
            assertFalse(path[i] == 2 && path[i + 1] == 1);
        }
    }

    @Test
    void startAtGoalAndBlockedEndpointsKeepTheirContracts() {
        setOmission(true);
        NavigationGrid grid = openGrid(3, 1);
        byte[] occupancy = {(byte) 255, 0, (byte) 255};
        assertArrayEquals(new int[]{0, 0}, GridPathfinder.findPath(grid, 0, 0, 0, 0, occupancy));
        assertSame(GridPathfinder.EMPTY_PATH, GridPathfinder.findPath(grid, 0, 0, 2, 0,
                true, occupancy, null, new boolean[]{true, true, false}));
        grid.setWalkable(2, 0, false);
        assertSame(GridPathfinder.EMPTY_PATH, GridPathfinder.findPath(grid, 0, 0, 2, 0, occupancy));
    }

    @Test
    void absentAndZeroOccupancyLeaveRouteUnchanged() {
        NavigationGrid grid = openGrid(7, 5);
        grid.setWalkable(3, 2, false);
        for (byte[] occupancy : new byte[][]{null, new byte[35]}) {
            setOmission(false);
            int[] before = GridPathfinder.findPath(grid, 0, 2, 6, 2, false, occupancy);
            setOmission(true);
            assertArrayEquals(before, GridPathfinder.findPath(grid, 0, 2, 6, 2, false, occupancy));
        }
    }

    @Test
    void sampledFallbackDoesNotDoubleCountOuterScopeOrKeepOldExpansions() {
        setOmission(true);
        NavigationGrid grid = openGrid(3, 1);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        GridPathfinder.findPathWithCostSampled(grid, 0, 0, 2, 0,
                true, new byte[]{0, 0, (byte) 255}, null, "UNCOVERED_START");
        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
        assertEquals(3, profile.pathfindExpandedNodes());
        assertEquals(255, profile.slowPathSearches().get(0).goalOccupancy());
        assertEquals("UNCOVERED_START", profile.slowPathSearches().get(0).fallbackReason());
        TickInnerProfile rejected = new TickInnerProfile();
        TickInnerProfile.setCurrent(rejected);
        GridPathfinder.findPathWithCostSampled(grid, 0, 0, -1, 0,
                true, null, null, "MISSING_FIELD");
        assertEquals(0, rejected.pathfindExpandedNodes());
        assertEquals(-1, rejected.slowPathSearches().get(0).goalOccupancy());
    }

    @Test
    void asynchronousSearchStillHonorsCancellation() {
        setOmission(true);
        NavigationGrid grid = openGrid(48, 32);
        byte[] occupancy = new byte[48 * 32];
        Arrays.fill(occupancy, (byte) 255);
        Thread.currentThread().interrupt();
        try {
            assertSame(GridPathfinder.EMPTY_PATH,
                    GridPathfinder.findPathAsyncUnprofiled(grid, 0, 0, 47, 31, true, occupancy));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertTrue(GridPathfinder.findPathAsyncUnprofiled(grid, 0, 0, 47, 31,
                true, occupancy).length > 0, "cancelled workspace remains reusable");
    }

    private static void setOmission(boolean value) {
        System.setProperty(GridPathfinder.OMIT_FIXED_GOAL_OCCUPANCY_PROPERTY, Boolean.toString(value));
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static void assertOptimal(NavigationGrid grid, int[] path, boolean cardinal,
                                      byte[] occupancy, float[] costs, boolean[] clearance) {
        double best = dijkstra(grid, cardinal, occupancy, costs, clearance);
        if (Double.isInfinite(best)) {
            assertSame(GridPathfinder.EMPTY_PATH, path);
            return;
        }
        assertTrue(path.length > 0);
        assertEquals(0, path[0]);
        assertEquals(0, path[1]);
        assertEquals(8, path[path.length - 2]);
        assertEquals(6, path[path.length - 1]);
        double actual = 0;
        for (int i = 2; i < path.length; i += 2) {
            int x = path[i - 2], y = path[i - 1], nx = path[i], ny = path[i + 1];
            Direction direction = Arrays.stream(Direction.ALL)
                    .filter(d -> d.dx == nx - x && d.dy == ny - y).findFirst().orElseThrow();
            assertTrue(legal(grid, x, y, direction, clearance));
            assertTrue(!cardinal || !direction.isDiagonal());
            int index = grid.index(nx, ny);
            actual += (direction.isDiagonal() ? Math.sqrt(2) : 1) * costs[index]
                    + 2 * (occupancy[index] & 255);
        }
        assertEquals(best, actual, .0002, "route must minimize original cost including terminal occupancy");
    }

    /** Tiny O(V^2) Dijkstra: no production heap, heuristic, or cost helper. */
    private static double dijkstra(NavigationGrid grid, boolean cardinal, byte[] occupancy,
                                   float[] costs, boolean[] clearance) {
        int count = costs.length, width = grid.getWidth();
        double[] distances = new double[count];
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        boolean[] visited = new boolean[count];
        distances[0] = 0;
        for (int iteration = 0; iteration < count; iteration++) {
            int current = -1;
            for (int i = 0; i < count; i++) {
                if (!visited[i] && (current < 0 || distances[i] < distances[current])) current = i;
            }
            if (current < 0 || Double.isInfinite(distances[current])) break;
            if (current == count - 1) return distances[current];
            visited[current] = true;
            int x = current % width, y = current / width;
            for (Direction direction : cardinal ? Direction.CARDINALS : Direction.ALL) {
                if (!legal(grid, x, y, direction, clearance)) continue;
                int next = grid.index(x + direction.dx, y + direction.dy);
                double candidate = distances[current]
                        + (direction.isDiagonal() ? Math.sqrt(2) : 1) * costs[next]
                        + 2 * (occupancy[next] & 255);
                distances[next] = Math.min(distances[next], candidate);
            }
        }
        return distances[count - 1];
    }

    private static boolean legal(NavigationGrid grid, int x, int y, Direction d, boolean[] clearance) {
        int nx = x + d.dx, ny = y + d.dy;
        if (!grid.isWalkable(nx, ny) || (clearance != null && !clearance[grid.index(nx, ny)])) return false;
        if (!grid.isEdgePassable(x, y, d) || !grid.isEdgePassable(nx, ny, d.opposite())) return false;
        if (!d.isDiagonal()) return true;
        Direction horizontal = d.dx > 0 ? Direction.E : Direction.W;
        Direction vertical = d.dy > 0 ? Direction.N : Direction.S;
        return grid.isWalkable(nx, y) && grid.isWalkable(x, ny)
                && grid.isEdgePassable(x, y, horizontal) && grid.isEdgePassable(x, y, vertical)
                && grid.isEdgePassable(nx, ny, horizontal.opposite())
                && grid.isEdgePassable(nx, ny, vertical.opposite());
    }
}
