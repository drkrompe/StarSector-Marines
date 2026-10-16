package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Low Quay's routes are the point of its layout: the carrier, the inbound technician and the
 * rearguard must all meet at the lane crossing, and only the secure gate joins compound and apron.
 * Generates a whole map, so it is evidence rather than a unit test.
 */
@Tag("generated-space-evidence")
class LowQuayRouteGeometryTest {
    private static MapResult map;

    @BeforeAll static void generate() { map = LowQuay.generate(15302, TargetProfile.NEUTRAL); }

    @Test void carrierLaneLeavesThroughTheGateAndOverTheCrossing() {
        NavigationGrid grid = map.grid;
        var mask = VehicleClearance.erode(grid, VehicleClearance.radiusForWidth(VehicleType.CARGO_CARRIER.visualWidthCells));
        int[] path = GridPathfinder.findPathOnDemand(grid, LowQuay.LOAD_X, LowQuay.LOAD_Y,
                LowQuay.LANDING_X, LowQuay.LANDING_Y, index -> 1f, mask::isPassableAt);
        assertTrue(path.length >= 4, "carrier needs a clear route to the apron");
        assertTrue(passesNear(path, LowQuay.CROSS_X, LowQuay.CROSS_Y, 3), "carrier route crosses the crossing");
        for (int i = 0; i < path.length; i += 2) if (path[i] == LowQuay.GATE_X)
            assertTrue(path[i + 1] >= LowQuay.GATE_BOTTOM && path[i + 1] <= LowQuay.GATE_TOP, "carrier leaves by the gate");
    }

    @Test void theSecureGateIsTheOnlyWesternOpening() {
        for (int y = 15; y < 100; y++) {
            boolean gate = y >= LowQuay.GATE_BOTTOM && y <= LowQuay.GATE_TOP;
            assertTrue(map.grid.isWalkable(LowQuay.GATE_X, y) == gate, "western wall at y=" + y);
        }
    }

    @Test void technicianWalksInAcrossTheCrossingAndDownTheSouthGallery() {
        int[] path = GridPathfinder.findPath(map.grid, 28, 59, LowQuay.KIT_X, LowQuay.KIT_Y);
        assertTrue(path.length > 0);
        assertTrue(passesNear(path, LowQuay.CROSS_X, LowQuay.CROSS_Y, 4), () -> "technician crosses at the crossing: " + Arrays.toString(path));
        // South gallery interior: x 62..65 below the lane's south vestibule.
        assertTrue(passesThrough(path, 62, 31, 65, 50), () -> "technician uses the south service gallery: " + Arrays.toString(path));
    }

    @Test void rearguardComesDownTheNorthGallery() {
        int[] path = GridPathfinder.findPath(map.grid, LowQuay.RESERVE_X, LowQuay.RESERVE_Y, LowQuay.CROSS_X, LowQuay.CROSS_Y);
        assertTrue(path.length > 0);
        // North gallery interior: x 62..65 between its two vestibules.
        assertTrue(passesThrough(path, 62, 68, 65, 85), () -> "rearguard reaches the crossing through the north gallery: " + Arrays.toString(path));
    }

    @Test void workRoomsAreTheArchiveAndTheShop() {
        assertTrue(LowQuay.insideRoom(map.topology, LowQuay.RECORDS_X, LowQuay.RECORDS_Y, true));
        assertTrue(LowQuay.insideRoom(map.topology, LowQuay.KIT_X, LowQuay.KIT_Y, false));
        assertFalse(LowQuay.insideRoom(map.topology, LowQuay.CROSS_X, LowQuay.CROSS_Y, true));
        assertFalse(LowQuay.insideRoom(map.topology, LowQuay.CROSS_X, LowQuay.CROSS_Y, false));
    }

    private static boolean passesNear(int[] path, int x, int y, int radius) {
        return passesThrough(path, x - radius, y - radius, x + radius, y + radius);
    }

    /** Whether any point on the path, sampled along each leg between waypoints, lies in the rectangle. */
    private static boolean passesThrough(int[] path, int l, int b, int r, int t) {
        for (int i = 0; i + 1 < path.length; i += 2) {
            int nx = i + 3 < path.length ? path[i + 2] : path[i], ny = i + 3 < path.length ? path[i + 3] : path[i + 1];
            int steps = Math.max(1, Math.max(Math.abs(nx - path[i]), Math.abs(ny - path[i + 1])));
            for (int s = 0; s <= steps; s++) {
                double x = path[i] + (nx - path[i]) * (double) s / steps, y = path[i + 1] + (ny - path[i + 1]) * (double) s / steps;
                if (x >= l && x <= r && y >= b && y <= t) return true;
            }
        }
        return false;
    }
}
