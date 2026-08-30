package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shared reverse tree grown under a route costing. The interesting half is
 * not that the route bends - {@link GridPathfinder} has honoured a cost field
 * for a long time - but that a tree remembers <em>which</em> costing it was
 * grown under, since it outlives the request that built it.
 */
class SharedGoalCostedFieldTest {

    private static final int W = 21;
    private static final int H = 9;
    private static final int LANE = 4;

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    /** Everything on row {@code y} dearer to cross, the rest at baseline. */
    private static RouteCostField dearRow(NavigationGrid grid, int y,
                                          float multiplier) {
        float[] cells = new float[W * H];
        Arrays.fill(cells, 1f);
        for (int x = 0; x < W; x++) cells[grid.index(x, y)] = multiplier;
        return new RouteCostField(cells, RouteCostField.nextRevision());
    }

    private static int cellsOnRow(int[] path, int y) {
        int found = 0;
        for (int cell = 0; cell < Paths.cellCount(path); cell++) {
            if (Paths.cellY(path, cell) == y) found++;
        }
        return found;
    }

    private static boolean visits(int[] path, int x, int y) {
        for (int cell = 0; cell < Paths.cellCount(path); cell++) {
            if (Paths.cellX(path, cell) == x && Paths.cellY(path, cell) == y) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aRouteLeavesTheDearestGroundWhenSteppingAsideIsCheaper() {
        NavigationGrid grid = openGrid();
        SharedGoalPathfinder pathfinder =
                new SharedGoalPathfinder(grid, new byte[W * H]);
        pathfinder.beginSnapshot();

        int[] straight = pathfinder.findPath(0, LANE, W - 1, LANE, true, null);
        assertEquals(W, cellsOnRow(straight, LANE),
                "with nothing to avoid, the walk down the lane is the walk");

        int[] costed = pathfinder.findPath(0, LANE, W - 1, LANE, true,
                dearRow(grid, LANE, 2f));
        assertTrue(cellsOnRow(costed, LANE) <= 3,
                "the lane is left almost immediately and rejoined at the end, "
                        + "not walked: " + cellsOnRow(costed, LANE) + " cells on it");
        int last = Paths.cellCount(costed) - 1;
        assertEquals(W - 1, Paths.cellX(costed, last), "and it still arrives");
        assertEquals(LANE, Paths.cellY(costed, last));
    }

    @Test
    void dearGroundIsDiscouragingRatherThanForbidden() {
        NavigationGrid grid = openGrid();
        // Wall the map off except for one dear cell, so the only way through
        // is over it. A costing must never turn a route into no route.
        for (int y = 0; y < H; y++) {
            if (y != LANE) grid.setWalkable(10, y, false);
        }
        SharedGoalPathfinder pathfinder =
                new SharedGoalPathfinder(grid, new byte[W * H]);
        pathfinder.beginSnapshot();

        int[] path = pathfinder.findPath(0, LANE, W - 1, LANE, true,
                dearRow(grid, LANE, 8f));
        assertTrue(Paths.cellCount(path) > 0, "the squad still gets there");
        assertTrue(visits(path, 10, LANE), "over the only ground there is");
    }

    @Test
    void twoCostingsAreTwoTreesAndTheOldOneIsNeverServedAgain() {
        NavigationGrid grid = openGrid();
        SharedGoalPathfinder pathfinder =
                new SharedGoalPathfinder(grid, new byte[W * H]);
        pathfinder.beginSnapshot();

        int[] whileTheLaneIsDear = pathfinder.findPath(0, LANE, W - 1, LANE,
                true, dearRow(grid, LANE, 2f));
        assertEquals(1, pathfinder.retainedFieldCountForTest());
        assertTrue(cellsOnRow(whileTheLaneIsDear, LANE) <= 3);

        // A republished costing that has forgotten the lane and now finds the
        // row beside it expensive instead. Were the tree keyed on the goal
        // alone this would be a cache hit, and the old avoidance would be
        // served for as long as the tree was retained.
        int[] onceItHasDecayed = pathfinder.findPath(0, LANE, W - 1, LANE,
                true, dearRow(grid, LANE - 1, 2f));
        assertEquals(2, pathfinder.retainedFieldCountForTest(),
                "the second costing grew its own tree");
        assertEquals(W, cellsOnRow(onceItHasDecayed, LANE),
                "and the lane is walked again now that nobody died in it");
    }

    @Test
    void anUncostedRequestIsUnaffectedByOneMadeUnderACosting() {
        NavigationGrid grid = openGrid();
        SharedGoalPathfinder pathfinder =
                new SharedGoalPathfinder(grid, new byte[W * H]);
        pathfinder.beginSnapshot();

        int[] before = pathfinder.findPath(0, LANE, W - 1, LANE, true, null);
        pathfinder.findPath(0, LANE, W - 1, LANE, true, dearRow(grid, LANE, 4f));
        int[] after = pathfinder.findPath(0, LANE, W - 1, LANE, true, null);

        assertTrue(Arrays.equals(before, after),
                "a side with no memory of losses walks where it always did");
    }
}
