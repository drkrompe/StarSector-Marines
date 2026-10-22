package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmFlowFieldsTest {

    private static final int GOAL = 0;

    /** 20x9 open floor with a wall down x=10 that has one gap at y=7. */
    private static NavigationGrid walledGrid() {
        NavigationGrid grid = new NavigationGrid(20, 9);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 0; y < 9; y++) {
            if (y != 7) grid.setWalkable(10, y, false);
        }
        return grid;
    }

    private static SwarmFlowFields.Field build(NavigationGrid grid, int tick, int... cells) {
        SwarmFlowFields fields = grid.swarmFlowFields();
        fields.refresh(GOAL, cells, cells.length, tick);
        return fields.field(GOAL);
    }

    @Test
    void distanceIsStepCostToTheNearestSource() {
        NavigationGrid grid = new NavigationGrid(10, 1);
        for (int x = 0; x < 10; x++) grid.setWalkableFloor(x, 0);
        SwarmFlowFields.Field field = build(grid, 0, grid.index(0, 0), grid.index(9, 0));

        assertEquals(0, field.distanceAt(0, 0));
        assertEquals(4 * SwarmFlowFields.CARDINAL_COST, field.distanceAt(4, 0));
        assertEquals(3 * SwarmFlowFields.CARDINAL_COST, field.distanceAt(6, 0));
    }

    @Test
    void descentGoesRoundAWallThroughItsGapAndNeverUphill() {
        NavigationGrid grid = walledGrid();
        SwarmFlowFields.Field field = build(grid, 0, grid.index(18, 1));

        int[] path = field.descend(grid, 2, 1, 64);
        assertTrue(Paths.cellCount(path) > 2);
        assertEquals(2, Paths.cellX(path, 0));
        boolean usedGap = false;
        for (int i = 1; i < Paths.cellCount(path); i++) {
            int x = Paths.cellX(path, i);
            int y = Paths.cellY(path, i);
            assertTrue(grid.isWalkable(x, y), "stepped into a wall at " + x + "," + y);
            assertTrue(field.distanceAt(x, y) < field.distanceAt(
                    Paths.cellX(path, i - 1), Paths.cellY(path, i - 1)));
            if (x == 10) usedGap = y == 7;
        }
        assertTrue(usedGap, "crossed the wall somewhere other than its gap");
        assertEquals(18, Paths.destX(path));
        assertEquals(1, Paths.destY(path));
    }

    /** 20 wide, {@code height} tall: a wall down x=10 with a gap at the bottom row and a barricade piece at y=1. */
    private static NavigationGrid barricadedGrid(int height) {
        NavigationGrid grid = new NavigationGrid(20, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 0; y < height - 1; y++) grid.setWalkable(10, y, false);
        grid.setTag(10, 1, NavigationGrid.CellTag.BARRICADE, true);
        return grid;
    }

    @Test
    void aLongWayRoundIsTornThroughAndTheDescentStopsAtThePiece() {
        NavigationGrid grid = barricadedGrid(30);
        SwarmFlowFields.Field field = build(grid, 0, grid.index(18, 1));

        int[] path = field.descend(grid, 2, 1, 64);
        assertEquals(9, Paths.destX(path), "stops in front of the barricade");
        assertEquals(1, Paths.destY(path));
        assertEquals(grid.index(10, 1), field.barricadeAhead(grid, 9, 1));
        assertTrue(field.leadsThroughBarricade(grid, 2, 1, 14));
        assertEquals(-1, field.barricadeAhead(grid, 8, 1), "not yet at it");
    }

    @Test
    void aShortWayRoundIsWalked() {
        NavigationGrid grid = barricadedGrid(4);
        SwarmFlowFields.Field field = build(grid, 0, grid.index(18, 1));

        int[] path = field.descend(grid, 2, 1, 64);
        assertEquals(18, Paths.destX(path), "walks round through the gap");
        assertFalse(field.leadsThroughBarricade(grid, 2, 1, 14));
    }

    @Test
    void descentStepsDiagonallyWhenThatIsNearest() {
        NavigationGrid grid = new NavigationGrid(6, 6);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) grid.setWalkableFloor(x, y);
        }
        SwarmFlowFields.Field field = build(grid, 0, grid.index(5, 5));

        int[] path = field.descend(grid, 0, 0, 16);
        assertEquals(6, Paths.cellCount(path), "five diagonal steps, no staircase");
        assertEquals(5 * SwarmFlowFields.DIAGONAL_COST, field.distanceAt(0, 0));
    }

    @Test
    void unreachableAndSourceCellsYieldNoPath() {
        NavigationGrid grid = walledGrid();
        grid.setWalkable(10, 7, false);
        SwarmFlowFields.Field field = build(grid, 0, grid.index(18, 1));

        assertEquals(SwarmFlowFields.UNREACHABLE, field.distanceAt(2, 1));
        assertEquals(0, Paths.cellCount(field.descend(grid, 2, 1, 8)));
        assertEquals(0, Paths.cellCount(field.descend(grid, 18, 1, 8)));
    }

    @Test
    void rebuildsOnTopologyOrSourceChangeOnlyAtTheBoundedCadence() {
        NavigationGrid grid = walledGrid();
        SwarmFlowFields fields = grid.swarmFlowFields();
        int[] sources = {grid.index(18, 1)};
        assertTrue(fields.refresh(GOAL, sources, 1, 0));
        assertFalse(fields.refresh(GOAL, sources, 1, 50), "nothing moved");

        grid.setWalkable(10, 3, true);
        assertTrue(fields.refresh(GOAL, sources, 1, 51), "a breach retires the field");
        assertEquals(51, fields.field(GOAL).builtTick());

        int[] moved = {grid.index(17, 1)};
        int tooSoon = 51 + SwarmFlowFields.REFRESH_TICKS - 1;
        assertFalse(fields.refresh(GOAL, moved, 1, tooSoon), "inside the cadence");
        assertTrue(fields.refresh(GOAL, moved, 1, tooSoon + 1));
        assertEquals(0, fields.field(GOAL).distanceAt(17, 1));
    }

    @Test
    void slicedRebuildKeepsTheOldFieldUntilItPublishesTheSameAnswer() {
        NavigationGrid grid = walledGrid();
        SwarmFlowFields sliced = new SwarmFlowFields(grid, 16);
        int[] first = {grid.index(18, 1)};
        int tick = 0;
        while (!sliced.refresh(GOAL, first, 1, tick)) {
            assertNull(sliced.field(GOAL), "nothing is published before the first build completes");
            tick++;
        }
        assertTrue(tick > 2, "180 cells at 16 a tick should take several ticks");
        SwarmFlowFields.Field old = sliced.field(GOAL);

        int[] moved = {grid.index(3, 8)};
        int restart = tick + SwarmFlowFields.REFRESH_TICKS;
        int t = restart;
        while (!sliced.refresh(GOAL, moved, 1, t)) {
            assertTrue(sliced.field(GOAL) == old, "the previous field stays up while the next builds");
            t++;
        }
        SwarmFlowFields.Field whole = build(walledGrid(), 0, moved);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 20; x++) {
                assertEquals(whole.distanceAt(x, y), sliced.field(GOAL).distanceAt(x, y));
            }
        }
        assertEquals(restart, sliced.field(GOAL).builtTick());
    }

    @Test
    void sourceOrderDoesNotChangeTheField() {
        NavigationGrid a = walledGrid();
        NavigationGrid b = walledGrid();
        int[] forward = {a.index(18, 1), a.index(3, 8), a.index(15, 6)};
        int[] reversed = {forward[2], forward[1], forward[0]};
        SwarmFlowFields.Field fa = build(a, 0, forward);
        SwarmFlowFields.Field fb = build(b, 0, reversed);
        int[] da = new int[20 * 9];
        int[] db = new int[20 * 9];
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 20; x++) {
                da[y * 20 + x] = fa.distanceAt(x, y);
                db[y * 20 + x] = fb.distanceAt(x, y);
            }
        }
        assertArrayEquals(da, db);
        assertFalse(b.swarmFlowFields().refresh(GOAL, forward, 3, 100),
                "the same set of sources in another order is not a change");
    }

    @Test
    void fieldsBelongToTheirOwnGridAndRetireWhenUnwanted() {
        NavigationGrid a = walledGrid();
        NavigationGrid b = walledGrid();
        build(a, 0, a.index(18, 1));
        assertNull(b.swarmFlowFields().field(GOAL));

        SwarmFlowFields fields = a.swarmFlowFields();
        fields.request(GOAL, 5);
        fields.retireUnused(5 + SwarmFlowFields.RETIRE_AFTER_TICKS);
        assertNotNull(fields.field(GOAL));
        fields.retireUnused(5 + SwarmFlowFields.RETIRE_AFTER_TICKS + 1);
        assertNull(fields.field(GOAL));
    }
}
