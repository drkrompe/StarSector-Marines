package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VehicleClearanceCache}: it must hand back the same arrays for as long
 * as they are still true, and it must not hand back one that has stopped being
 * true. The second half is the one that matters — a stale mask is silent, and
 * what it costs is a proved route through ground nothing can drive on any more.
 */
public class VehicleClearanceCacheTest {

    /** Rectangular block of walkable floor [x0..x1] × [y0..y1] inclusive. */
    private static void carve(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
    }

    @Test
    public void anUnchangedGridIsErodedAndLabelledOnce() {
        NavigationGrid grid = new NavigationGrid(16, 8);
        carve(grid, 1, 1, 14, 6);
        VehicleClearanceCache cache = new VehicleClearanceCache(1);

        VehicleClearance first = cache.clearance(grid, grid.topologyRevision());
        ClearanceComponents labels = cache.components(grid, grid.topologyRevision());

        assertSame(first, cache.clearance(grid, grid.topologyRevision()));
        assertSame(labels, cache.components(grid, grid.topologyRevision()));
        assertEquals(1, cache.clearanceBuilds());
        assertEquals(1, cache.componentBuilds());
    }

    @Test
    public void aProbeThatOnlyWantsTheMaskDoesNotPayForTheLabels() {
        NavigationGrid grid = new NavigationGrid(16, 8);
        carve(grid, 1, 1, 14, 6);
        VehicleClearanceCache cache = new VehicleClearanceCache(1);

        cache.clearance(grid, grid.topologyRevision());
        cache.clearance(grid, grid.topologyRevision());

        assertEquals(1, cache.clearanceBuilds());
        assertEquals(0, cache.componentBuilds());
    }

    /**
     * The live case: an airframe settling onto open ground seals the cells its
     * hull covers ({@code AirframeFootprint.settleWreck} writes exactly this),
     * and a retained mask would keep offering a drive straight through it.
     */
    @Test
    public void aSettledWreckClosesTheCellItCameDownOnForTheNextAsk() {
        NavigationGrid grid = new NavigationGrid(16, 8);
        carve(grid, 1, 1, 14, 6);
        VehicleClearanceCache cache = new VehicleClearanceCache(0);

        VehicleClearance before = cache.clearance(grid, grid.topologyRevision());
        assertTrue(before.isPassable(7, 3));

        grid.setWalkable(7, 3, false);

        VehicleClearance after = cache.clearance(grid, grid.topologyRevision());
        assertNotSame(before, after);
        assertFalse(after.isPassable(7, 3));
        assertEquals(2, cache.clearanceBuilds());
    }

    /**
     * A closed edge is a wall the mask cannot see, so the labels depend on edge
     * passability as well as on cell flags — and the revision counts both.
     */
    @Test
    public void openingASharedEdgeRelabelsTheComponentsItJoins() {
        NavigationGrid grid = new NavigationGrid(12, 6);
        carve(grid, 1, 1, 10, 4);
        for (int y = 1; y <= 4; y++) {
            grid.blockSharedEdge(5, y, Direction.E);
        }
        VehicleClearanceCache cache = new VehicleClearanceCache(0);

        ClearanceComponents split = cache.components(grid, grid.topologyRevision());
        assertFalse(split.connected(2, 2, 8, 2));

        for (int y = 1; y <= 4; y++) {
            grid.openSharedEdge(5, y, Direction.E);
        }

        ClearanceComponents joined = cache.components(grid, grid.topologyRevision());
        assertNotSame(split, joined);
        assertTrue(joined.connected(2, 2, 8, 2));
        assertEquals(2, cache.componentBuilds());
    }

    /**
     * Two grids carved to the same cell count reach the same revision, so the
     * revision alone cannot separate them. Which battle a mask belongs to is
     * part of the key.
     */
    @Test
    public void aDifferentGridIsNeverAnsweredFromTheHeldOne() {
        NavigationGrid first = new NavigationGrid(12, 8);
        carve(first, 1, 1, 10, 4);
        NavigationGrid second = new NavigationGrid(12, 8);
        carve(second, 1, 1, 8, 5);
        assertEquals(first.topologyRevision(), second.topologyRevision());
        VehicleClearanceCache cache = new VehicleClearanceCache(0);

        cache.clearance(first, first.topologyRevision());
        VehicleClearance other = cache.clearance(second, second.topologyRevision());

        assertTrue(other.isPassable(2, 2));
        assertFalse(other.isPassable(9, 2));
        assertEquals(2, cache.clearanceBuilds());
    }
}
