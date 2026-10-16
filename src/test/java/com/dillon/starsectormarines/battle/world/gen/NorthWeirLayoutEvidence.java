package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evidence, not a test: the generated North Weir keeps the circulation Water Under Seal depends on
 * while it reads as a plant. Run with {@code gradlew.bat generatedSpaceEvidence}.
 */
@Tag("generated-space-evidence")
class NorthWeirLayoutEvidence {
    private static final long[] SEEDS = {15301L, 16101L, 16102L};

    @Test
    void plantKeepsLiftLoadTenderAndRoadCirculation() {
        for (long seed : SEEDS) {
            MapResult map = NorthWeir.generate(seed, null);
            for (LandingPad pad : map.landingPads) assertTrue(pad.isClear(map.grid, map.topology), "berth obstructed, seed " + seed);

            // The repair load still has a vehicle-clear lane from its start to the isolation switch.
            var mask = VehicleClearance.erode(map.grid, VehicleClearance.radiusForWidth(VehicleType.CARGO_CARRIER.visualWidthCells));
            int[] route = GridPathfinder.findPathOnDemand(map.grid, 9, 59, NorthWeir.ISOLATION_X, NorthWeir.ISOLATION_Y,
                    index -> 1f, mask::isPassableAt);
            assertTrue(route.length >= 4, "repair load has no cleared lane, seed " + seed);

            // The docked load is screened from both switching rooms.
            for (int y = 30; y <= 83; y++) for (int x = 70; x <= 90; x++) {
                boolean room = NorthWeir.insideRoom(map.topology, x, y, true) || NorthWeir.insideRoom(map.topology, x, y, false);
                if (!room) continue;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                    assertFalse(map.grid.hasLineOfSight(x, y, NorthWeir.ISOLATION_X + dx, NorthWeir.ISOLATION_Y + dy),
                            "switch room " + x + "," + y + " sees the docked load, seed " + seed);
            }

            // The repair watch's walk from the load to the north switch is out of the south switch's sight.
            int[] watch = GridPathfinder.findPath(map.grid, NorthWeir.ISOLATION_X, NorthWeir.ISOLATION_Y, NorthWeir.NORTH_X, NorthWeir.NORTH_Y);
            assertTrue(watch.length > 0);
            for (int i = 0; i < watch.length; i += 2) for (int y = 35; y <= 45; y++) for (int x = 74; x <= 86; x++)
                if (NorthWeir.insideRoom(map.topology, x, y, false))
                    assertFalse(map.grid.hasLineOfSight(x, y, watch[i], watch[i + 1]),
                            "south switch " + x + "," + y + " sees the repair watch at " + watch[i] + "," + watch[i + 1] + ", seed " + seed);

            // Tender working space and the gun's hardstand stay open ground.
            for (int y = 84; y <= 94; y++) for (int x = 103; x <= 114; x++)
                assertTrue(map.grid.isWalkable(x, y) && !map.topology.isFixture(x, y), "gun bay blocked at " + x + "," + y);

            // A service crossing joins the two switching rooms directly; the road and its flank both reach the gun.
            int[] crossing = GridPathfinder.findPath(map.grid, NorthWeir.NORTH_X, NorthWeir.NORTH_Y, NorthWeir.SOUTH_X, NorthWeir.SOUTH_Y);
            assertTrue(crossing.length > 0 && crossing.length / 2 < 70, "switch rooms lack a short crossing, seed " + seed);
            assertTrue(GridPathfinder.findPath(map.grid, 172, 88, NorthWeir.BUILD_X, NorthWeir.BUILD_Y).length > 0);
            assertTrue(GridPathfinder.findPath(map.grid, 134, NorthWeir.FLANK_Y, NorthWeir.BUILD_X, NorthWeir.BUILD_Y).length > 0);

            // Each switching room has its own works.
            int pumps = 0, tanks = 0;
            for (Doodad d : map.doodads) {
                String id = d.definitionId();
                if (id.equals(NorthWeir.PUMP_MOTOR) && inside(d, NorthWeir.PUMPS_LEFT, NorthWeir.PUMPS_BOTTOM, NorthWeir.PUMPS_RIGHT, NorthWeir.PUMPS_TOP)) pumps++;
                if (id.equals(NorthWeir.SETTLING_TANK) && inside(d, NorthWeir.TANKS_LEFT, NorthWeir.TANKS_BOTTOM, NorthWeir.TANKS_RIGHT, NorthWeir.TANKS_TOP)) tanks++;
            }
            assertTrue(pumps == 6 && tanks == 10, "works incomplete: pumps=" + pumps + " tanks=" + tanks + ", seed " + seed);
        }
    }

    private static boolean inside(Doodad d, int l, int b, int r, int t) {
        return d.cellX >= l && d.cellX <= r && d.cellY >= b && d.cellY <= t;
    }
}
