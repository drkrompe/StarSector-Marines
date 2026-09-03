package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VehicleClearanceCache}'s catch-up path: a mask that has already been
 * eroded once must answer a later, logged change by re-evaluating only the
 * Chebyshev neighbourhood that change could have flipped, not by sweeping the
 * whole grid again — and it must fall back to a full re-erosion the moment the
 * grid says the log can no longer account for everything that happened.
 */
class VehicleClearanceCacheCatchUpTest {

    /** Rectangular block of walkable floor [x0..x1] x [y0..y1] inclusive. */
    private static void carve(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
    }

    @Test
    void aWreckPlacedAfterTheBuildRecomputesOnlyItsOwnNeighbourhood() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        carve(grid, 1, 1, 38, 38);
        VehicleClearanceCache cache = new VehicleClearanceCache(1); // radius 1 -> 3x3 block

        VehicleClearance before = cache.clearance(grid, grid.topologyRevision());
        assertTrue(before.isPassable(20, 20));

        grid.setWalkable(20, 20, false); // a wreck lands in the interior, far from any edge

        VehicleClearance after = cache.clearance(grid, grid.topologyRevision());

        // The catch-up clones `before` (a bulk array copy, not a footprint
        // test) and then patches the changed neighbourhood, so the clone's own
        // fitEvaluations count is exactly what that patch spent: the
        // (2r+1)x(2r+1) = 3x3 block centred on the changed cell is interior (no
        // grid-edge clamping), so exactly 9 footprint tests answer the whole
        // catch-up -- not the 1,600 a fresh erosion of this grid would run.
        assertEquals(9, after.fitEvaluations());
        assertEquals(1, cache.clearanceBuilds());
        assertEquals(1, cache.clearanceCatchUps());

        // The mask actually changed where it should have...
        assertFalse(after.isPassable(20, 20));
        assertFalse(after.isPassable(19, 19));
        assertFalse(after.isPassable(21, 21));
        // ...and nowhere else: two cells outside the 3x3 neighbourhood, one of
        // which shares a side with it, both stay exactly as eroded.
        assertTrue(after.isPassable(22, 20));
        assertTrue(after.isPassable(10, 10));

        // The snapshot already handed out is untouched by the later catch-up.
        assertTrue(before.isPassable(20, 20));
    }

    @Test
    void moreChangesThanTheLogHoldsFallsBackToAFullRebuild() {
        NavigationGrid grid = new NavigationGrid(64, 64);
        carve(grid, 1, 1, 62, 62);
        VehicleClearanceCache cache = new VehicleClearanceCache(0);

        cache.clearance(grid, grid.topologyRevision());
        assertEquals(1, cache.clearanceBuilds());

        // Flip one cell back and forth more times than the log remembers, all
        // before the cache is asked again -- exactly the situation
        // changeLogCapacity() exists to name, and the one case a pure catch-up
        // cannot answer correctly. Reading the current value back each time
        // guarantees every call is a real, logged change rather than a
        // same-value no-op.
        int capacity = grid.changeLogCapacity();
        for (int i = 0; i <= capacity; i++) {
            grid.setWalkable(20, 20, !grid.isWalkable(20, 20));
        }

        VehicleClearance after = cache.clearance(grid, grid.topologyRevision());

        assertEquals(2, cache.clearanceBuilds());
        assertEquals(0, cache.clearanceCatchUps());
        // A full rebuild is exactly VehicleClearance.erode -- spot-check it
        // against the grid's own walkability at radius 0.
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assertEquals(grid.isWalkable(x, y), after.isPassable(x, y));
            }
        }
    }
}
