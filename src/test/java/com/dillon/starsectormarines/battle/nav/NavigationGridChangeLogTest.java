package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The changed-cell log is what lets a derivation of the grid recompute the
 * neighbourhood of a breach instead of the whole map, so what it must promise
 * is that every write which moves the revision also names its cell.
 */
class NavigationGridChangeLogTest {

    @Test
    void aWriteThatChangesNothingRecordsNothing() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        grid.setTag(3, 3, NavigationGrid.CellTag.WALKABLE, true);
        long count = grid.changeCount();
        long revision = grid.topologyRevision();

        grid.setTag(3, 3, NavigationGrid.CellTag.WALKABLE, true);

        assertEquals(count, grid.changeCount());
        assertEquals(revision, grid.topologyRevision());
    }

    @Test
    void everyRevisionBumpNamesItsCell() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        long start = grid.changeCount();

        grid.setTag(1, 2, NavigationGrid.CellTag.WALKABLE, true);
        grid.setEdgePassable(3, 4, Direction.E, true);
        grid.openAllEdges(5, 6);

        assertEquals(start + 3, grid.changeCount());
        assertEquals(grid.index(1, 2), grid.changedCellAt(start));
        assertEquals(grid.index(3, 4), grid.changedCellAt(start + 1));
        assertEquals(grid.index(5, 6), grid.changedCellAt(start + 2));
    }

    @Test
    void theRevisionAndTheChangeCountMoveTogether() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        long revisionStart = grid.topologyRevision();
        long countStart = grid.changeCount();

        grid.setTag(0, 0, NavigationGrid.CellTag.WALKABLE, true);
        grid.setTag(1, 0, NavigationGrid.CellTag.DOORWAY, true);
        grid.setSharedEdgePassable(2, 2, Direction.N, true);

        assertEquals(grid.topologyRevision() - revisionStart,
                grid.changeCount() - countStart);
    }

    @Test
    void aBreachNamesTheCellItOpened() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        grid.setWallHp(4, 4, 10);
        long start = grid.changeCount();

        assertTrue(grid.damageCell(4, 4, 10));

        assertEquals(start + 1, grid.changeCount());
        assertEquals(grid.index(4, 4), grid.changedCellAt(start));
    }

    @Test
    void aReaderWithinCapacityCatchesUpAndOneBeyondItDoesNot() {
        NavigationGrid grid = new NavigationGrid(256, 256);
        long start = grid.changeCount();
        int capacity = grid.changeLogCapacity();

        for (int i = 0; i < capacity; i++) {
            grid.setTag(i % 256, i / 256, NavigationGrid.CellTag.WALKABLE, true);
        }

        assertTrue(grid.hasCaughtUpFrom(start));
        // The oldest entry is still the one it would replay first.
        assertEquals(grid.index(0, 0), grid.changedCellAt(start));

        grid.setTag(0, 64, NavigationGrid.CellTag.DOORWAY, true);
        assertFalse(grid.hasCaughtUpFrom(start));
    }

    @Test
    void aWholeGridResetPutsEveryReaderPastTheLog() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        grid.setTag(1, 1, NavigationGrid.CellTag.WALKABLE, true);
        long caughtUp = grid.changeCount();
        long revision = grid.topologyRevision();

        grid.clear();

        assertFalse(grid.hasCaughtUpFrom(caughtUp));
        assertTrue(grid.topologyRevision() > revision);
    }
}
