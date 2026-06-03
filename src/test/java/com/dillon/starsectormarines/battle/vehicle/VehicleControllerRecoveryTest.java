package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entity coverage for {@link VehicleController#maxReverseDistance} — the
 * "back up to where you actually can, accounting for what's behind you"
 * calculation that bounds the committed reverse recovery. It marches the
 * reverse axis footprint-checking each step, so a clear rear yields the full
 * budget, a wall partway back bounds it, and a wall right behind yields a
 * distance below the "don't bother" threshold (the boxed-in case the controller
 * uses to skip a maneuver that can't gain room).
 *
 * <p>Truck faces north (0°) throughout, so the reverse axis is due south (−Y):
 * the south wall of the carved block is what bounds the backup. HEAVY_APC is
 * 2.4 long × 1.4 wide, so its footprint half-length is 1.2 cells.
 */
public class VehicleControllerRecoveryTest {

    private static final float EPS = 1e-3f;

    /** Grid with a rectangular walkable block [x0..x1] × [y0..y1]; everything else wall. */
    private static NavigationGrid block(int w, int h, int x0, int y0, int x1, int y1) {
        NavigationGrid g = new NavigationGrid(w, h);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                g.setWalkableFloor(x, y);
        return g;
    }

    private static float reverse(NavigationGrid g, float x, float y) {
        return VehicleController.maxReverseDistance(x, y, 0f, VehicleType.HEAVY_APC, g);
    }

    @Test
    public void clearRearReturnsTheFullBudget() {
        // Wide-open block; backing south from mid-block never hits anything.
        NavigationGrid g = block(12, 16, 0, 0, 11, 15);
        assertEquals(VehicleController.REVERSE_RECOVERY_CELLS, reverse(g, 5.5f, 10.5f), EPS,
                "a clear rear backs up the full recovery budget");
    }

    @Test
    public void wallPartwayBehindBoundsTheDistance() {
        // Floor only at y>=6; truck at y=8.5 → rear sample at 7.3. Backing keeps
        // the rear ≥ row 6 until 7.3−d < 6 (d > 1.3), step-quantized to 1.25.
        NavigationGrid g = block(12, 16, 0, 6, 11, 15);
        float d = reverse(g, 5.5f, 8.5f);
        assertEquals(1.25f, d, EPS, "wall ~1.3 cells behind bounds the backup to 1.25");
        assertTrue(d < VehicleController.REVERSE_RECOVERY_CELLS, "and short of the full budget");
    }

    @Test
    public void wallImmediatelyBehindIsBelowTheUsefulThreshold() {
        // Floor only at y>=8; truck at y=9.2 is feasible (rear sample at 8.0),
        // but the first 0.25 back-step pushes the rear sample to floor 7 (< 8 wall).
        NavigationGrid g = block(12, 16, 0, 8, 11, 15);
        float d = reverse(g, 5.5f, 9.2f);
        assertEquals(0f, d, EPS, "boxed in behind → zero achievable backup");
        assertTrue(d < VehicleController.MIN_USEFUL_REVERSE_CELLS,
                "→ below the useful threshold, so the controller skips the maneuver");
    }
}
