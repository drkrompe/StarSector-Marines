package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GridPathfinder#labelConnectedComponents} exists so callers holding a
 * grid fixed can answer "is there a route" without running a search. That is
 * only sound if it agrees with the search on every pair, so these tests assert
 * the equivalence exhaustively on grids built to exercise the awkward parts of
 * the step rule — diagonal corner-cutting and one-sided edge barriers.
 */
class GridPathfinderComponentsTest {

    private static final int W = 12;
    private static final int H = 10;

    @Test
    void agreesWithPathfinderOnEveryPairAcrossManyRandomGrids() {
        for (long seed = 0; seed < 40; seed++) {
            NavigationGrid grid = randomGrid(seed);
            int[] component = GridPathfinder.labelConnectedComponents(grid);

            for (int ay = 0; ay < H; ay++) {
                for (int ax = 0; ax < W; ax++) {
                    for (int by = 0; by < H; by++) {
                        for (int bx = 0; bx < W; bx++) {
                            boolean viaSearch = GridPathfinder
                                    .findPathWithoutComponentCheck(grid,
                                            ax, ay, bx, by,
                                            GridPathfinder.USE_CARDINAL_NAVIGATION)
                                    .length > 0;
                            int from = component[ay * W + ax];
                            boolean viaComponents =
                                    from >= 0 && from == component[by * W + bx];

                            assertEquals(viaSearch, viaComponents,
                                    "seed " + seed + ": (" + ax + "," + ay + ") -> ("
                                            + bx + "," + by + ")");
                        }
                    }
                }
            }
        }
    }

    @Test
    void labelsNonWalkableCellsAsNoComponent() {
        NavigationGrid grid = new NavigationGrid(W, H);
        openAllEdges(grid);
        grid.setWalkable(3, 3, true);

        int[] component = GridPathfinder.labelConnectedComponents(grid);

        assertTrue(component[3 * W + 3] >= 0, "walkable cell should carry a component");
        assertEquals(-1, component[0], "non-walkable cell should carry no component");
    }

    @Test
    void separatesRegionsSplitByAWall() {
        // Two open halves divided by a full-height wall column at x = 5.
        NavigationGrid grid = new NavigationGrid(W, H);
        openAllEdges(grid);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkable(x, y, x != 5);
            }
        }

        int[] component = GridPathfinder.labelConnectedComponents(grid);
        int left = component[0 * W + 4];
        int right = component[0 * W + 6];

        assertTrue(left >= 0 && right >= 0);
        assertFalse(left == right, "a full-height wall must separate the halves");
        assertEquals(0, GridPathfinder.findPath(grid, 4, 0, 6, 0).length,
                "the search must agree that the halves do not connect");
    }

    @Test
    void cachedConnectivityInvalidatesWhenTopologyOpensAPath() {
        NavigationGrid grid = new NavigationGrid(5, 1);
        for (int x = 0; x < 5; x++) grid.setWalkableFloor(x, 0);
        grid.setWalkable(2, 0, false);

        assertEquals(0, GridPathfinder.findPath(grid, 0, 0, 4, 0).length);

        grid.setWalkableFloor(2, 0);

        int[] opened = GridPathfinder.findPath(grid, 0, 0, 4, 0);
        assertEquals(4, Paths.destX(opened));
        assertEquals(0, Paths.destY(opened));
    }

    @Test
    void cachesCardinalAndDiagonalConnectivitySeparately() {
        NavigationGrid grid = new NavigationGrid(2, 2);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                grid.setWalkable(x, y, true);
                for (Direction direction : Direction.ALL) {
                    grid.setEdgePassable(x, y, direction, true);
                }
            }
        }
        // Prevent both cardinal routes from (0,0) to (1,1), while leaving the
        // endpoints' diagonal and cardinal edge bits open for the direct step.
        grid.setEdgePassable(1, 0, Direction.W, false);
        grid.setEdgePassable(0, 1, Direction.S, false);

        assertTrue(grid.arePathConnected(0, 0, 1, 1, false));
        assertFalse(grid.arePathConnected(0, 0, 1, 1, true));
        assertTrue(GridPathfinder.findPathWithoutComponentCheck(
                grid, 0, 0, 1, 1, false).length > 0);
        assertEquals(0, GridPathfinder.findPathWithoutComponentCheck(
                grid, 0, 0, 1, 1, true).length);
    }

    /**
     * A grid with random walls and random one-directional edge barriers. The
     * barriers matter: the step rule checks passability from both sides, and a
     * component labeling is only valid because that makes the relation
     * symmetric.
     */
    private static NavigationGrid randomGrid(long seed) {
        Random random = new Random(seed);
        NavigationGrid grid = new NavigationGrid(W, H);

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkable(x, y, random.nextInt(100) < 70);
                for (Direction direction : Direction.ALL) {
                    grid.setEdgePassable(x, y, direction, random.nextInt(100) < 85);
                }
            }
        }
        return grid;
    }

    private static void openAllEdges(NavigationGrid grid) {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                for (Direction direction : Direction.ALL) {
                    grid.setEdgePassable(x, y, direction, true);
                }
            }
        }
    }
}
