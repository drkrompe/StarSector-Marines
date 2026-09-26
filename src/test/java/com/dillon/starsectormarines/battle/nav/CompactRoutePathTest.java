package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Storage changes must not change the arithmetic, tie ordering, or pinned cost epoch. */
class CompactRoutePathTest {
    private static final int WIDTH = 23;
    private static final int HEIGHT = 13;
    private static final int BLOCK = 4;

    @Test
    void occupancyIsAddedAfterTheRouteMultiplierAndReadUnsigned() {
        byte[] occupancy = {2, (byte) 200};
        assertEquals(1.5f + 2f * GridPathfinder.OCCUPANCY_PENALTY,
                GridPathfinder.stepCost(GridPathfinder.FIRST_CARDINAL_DIRECTION,
                        0, occupancy, 1.5f));
        assertEquals(1.5f + 200f * GridPathfinder.OCCUPANCY_PENALTY,
                GridPathfinder.stepCost(GridPathfinder.FIRST_CARDINAL_DIRECTION,
                        1, occupancy, 1.5f));
    }

    @Test
    void flatSharedAndCorridorSearchesMatchDenseCostsExactly() {
        NavigationGrid grid = grid();
        RouteCostField compact = compactCost();
        float[] cells = expanded(compact);
        RouteCostField dense = new RouteCostField(cells, RouteCostField.nextRevision());
        byte[] occupancy = new byte[cells.length];
        for (int cell = 0; cell < cells.length; cell += 11) occupancy[cell] = 2;
        occupancy[grid.index(21, 11)] = (byte) 200;
        int[] starts = {grid.index(0, 0), grid.index(2, 10), grid.index(22, 12)};
        int[] corridor = IntStream.range(0, cells.length).toArray();
        for (boolean cardinal : new boolean[]{true, false}) {
            SharedGoalPathfinder shared = new SharedGoalPathfinder(grid, occupancy);
            // Before beginSnapshot this is the ordinary costed fallback path.
            for (int start : starts) {
                int sx = start % WIDTH;
                int sy = start / WIDTH;
                int[] expected = GridPathfinder.findPath(grid, sx, sy, 21, 11,
                        cardinal, occupancy, cells, null);
                assertFalse(Paths.isEmpty(expected));
                assertArrayEquals(expected, GridPathfinder.findPathWithCost(grid,
                        sx, sy, 21, 11, cardinal, occupancy, compact));
                assertArrayEquals(expected, GridPathfinder.findPathWithCostUnprofiled(grid,
                        sx, sy, 21, 11, cardinal, occupancy, compact));
                assertArrayEquals(expected, shared.findPath(sx, sy, 21, 11, cardinal, compact));
            }
            shared.beginSnapshot();
            for (int start : starts) {
                int sx = start % WIDTH;
                int sy = start / WIDTH;
                assertArrayEquals(shared.findPath(sx, sy, 21, 11, cardinal, dense),
                        shared.findPath(sx, sy, 21, 11, cardinal, compact));
            }
            assertEquals(2, shared.retainedFieldCountForTest(),
                    "equal values with distinct revisions still have separate cache identities");
            SquadRouteField.Builder builder = new SquadRouteField.Builder(grid);
            SquadRouteField expected = builder.build(corridor, dense,
                    grid.index(21, 11), starts, cardinal);
            SquadRouteField actual = builder.build(corridor, compact,
                    grid.index(21, 11), starts, cardinal);
            assertEquals(expected.settledCellCount(), actual.settledCellCount());
            for (int cell = 0; cell < cells.length; cell++) {
                assertArrayEquals(expected.extract(cell % WIDTH, cell / WIDTH),
                        actual.extract(cell % WIDTH, cell / WIDTH));
            }
        }
    }

    @Test
    void squadPreparationAndFallbackKeepTheCompactSnapshotPinned() {
        NavigationGrid grid = grid();
        NavigationService compactNavigation = new NavigationService(grid, new CellTopology(WIDTH, HEIGHT));
        NavigationService denseNavigation = new NavigationService(grid, new CellTopology(WIDTH, HEIGHT));
        RouteCostField compact = compactCost();
        RouteCostField dense = new RouteCostField(expanded(compact), RouteCostField.nextRevision());
        Object token = new Object();
        int[] starts = {grid.index(2, 10)};
        compactNavigation.prepareSquadRoutes(List.of(new SquadRouteRequest(3, 1L, token,
                21, 11, starts, compact)));
        denseNavigation.prepareSquadRoutes(List.of(new SquadRouteRequest(3, 1L, token,
                21, 11, starts, dense)));
        assertEquals(1, compactNavigation.lastSquadRouteBuilds());
        assertEquals(denseNavigation.lastSquadRouteSettledCells(),
                compactNavigation.lastSquadRouteSettledCells());
        float[] baseline = new float[WIDTH * HEIGHT];
        Arrays.fill(baseline, 1f);
        RouteCostField newer = new RouteCostField(baseline, RouteCostField.nextRevision());
        // Uncovered starts fall back under the pinned old cost, not this newer one.
        for (int cell = 0; cell < WIDTH * HEIGHT; cell++) {
            assertArrayEquals(denseNavigation.findSquadPathToGoal(3, 1L, token,
                            cell % WIDTH, cell / WIDTH, 21, 11, newer),
                    compactNavigation.findSquadPathToGoal(3, 1L, token,
                            cell % WIDTH, cell / WIDTH, 21, 11, newer));
        }
        // A different intent without a prepared field reads the current compact costing.
        Object other = new Object();
        assertArrayEquals(GridPathfinder.findPath(grid, 0, 0, 21, 11,
                        GridPathfinder.USE_CARDINAL_NAVIGATION, null, expanded(compact), null),
                compactNavigation.findSquadPathToGoal(3, 2L, other, 0, 0, 21, 11, compact));
    }

    private static RouteCostField compactCost() {
        int columns = (WIDTH + BLOCK - 1) / BLOCK;
        float[] blocks = new float[columns * ((HEIGHT + BLOCK - 1) / BLOCK)];
        for (int i = 0; i < blocks.length; i++) blocks[i] = 1f + (i % 5) * 0.25f;
        return new RouteCostField.BlockLayout(WIDTH, HEIGHT, BLOCK)
                .snapshot(blocks, RouteCostField.nextRevision());
    }

    private static float[] expanded(RouteCostField compact) {
        float[] cells = new float[compact.size()];
        for (int i = 0; i < cells.length; i++) cells[i] = compact.costAt(i);
        return cells;
    }

    private static NavigationGrid grid() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 0; y < 9; y++) grid.setWalkable(10, y, false);
        grid.setSharedEdgePassable(6, 11, Direction.E, false);
        return grid;
    }
}
