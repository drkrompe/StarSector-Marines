package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class VehicleRescueRouteTest {

    @Test
    void prefersForwardFirstStepThenSkipsThatAttemptOnTheNextRescue() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        CellTopology topology = new CellTopology(12, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, GroundKind.GRASS);
        }
        TerrainCostField cost = TerrainCostField.from(topology);
        VehicleClearance clearance = VehicleClearance.erode(grid, 0);

        VehicleRoutePlanner.RescueRoute forward = VehicleRoutePlanner.routeAvoidingForwardFirst(
                5, 5, 9, 5, -90f, 0, grid, cost, clearance, 8, 8, 0f,
                VehicleType.HEAVY_APC);

        assertNotNull(forward);
        assertEquals(Direction.E.bit(), forward.firstStepDirectionBit());
        assertEquals(6.5f, forward.points()[0][1], 0.001f);
        assertEquals(5.5f, forward.points()[1][1], 0.001f);

        VehicleRoutePlanner.RescueRoute next = VehicleRoutePlanner.routeAvoidingForwardFirst(
                5, 5, 9, 5, -90f, 1 << Direction.E.bit(),
                grid, cost, clearance, 8, 8, 0f, VehicleType.HEAVY_APC);

        assertNotNull(next);
        assertEquals(Direction.NE.bit(), next.firstStepDirectionBit(),
                "once straight ahead was attempted, the next rescue must turn before backing up");
    }

    @Test
    void cumulativeAvoidanceCannotReturnThroughAnEarlierFailedTurn() {
        NavigationGrid grid = new NavigationGrid(14, 12);
        CellTopology topology = new CellTopology(14, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 14; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, GroundKind.GRASS);
        }
        TerrainCostField cost = TerrainCostField.from(topology);
        VehicleClearance clearance = VehicleClearance.erode(grid, 0);

        VehicleRoutePlanner.RescueRoute rescue = VehicleRoutePlanner.routeAvoidingForwardFirst(
                2, 5, 11, 5, -90f, 0, grid, cost, clearance,
                new int[]{5, 8}, new int[]{5, 5}, 2, 1f, VehicleType.HEAVY_APC);

        assertNotNull(rescue);
        assertFalse(covers(rescue.points(), 5, 5));
        assertFalse(covers(rescue.points(), 8, 5));
    }

    @Test
    void onDemandRecoveryMatchesEagerAvoidanceAndSnapping() {
        NavigationGrid grid = new NavigationGrid(14, 12);
        CellTopology topology = new CellTopology(14, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 14; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, GroundKind.GRASS);
        }
        TerrainCostField cost = TerrainCostField.from(topology);
        VehicleClearance clearance = VehicleClearance.erode(grid, 0);
        int[] avoidXs = {5, 8};
        int[] avoidYs = {5, 5};

        VehicleRoutePlanner.RescueRoute eager = VehicleRoutePlanner.routeAvoidingForwardFirst(
                2, 5, 11, 5, -90f, 0, grid, cost, clearance,
                avoidXs, avoidYs, 2, 1f, VehicleType.HEAVY_APC);
        VehicleRoutePlanner.RescueRoute lazy = VehicleRoutePlanner.routeAvoidingForwardFirstOnDemand(
                2, 5, 11, 5, -90f, 0, grid, cost::costAtIndex,
                index -> clearance.passableArray()[index],
                avoidXs, avoidYs, 2, 1f, VehicleType.HEAVY_APC);

        assertNotNull(lazy);
        assertEquals(eager.firstStepDirectionBit(), lazy.firstStepDirectionBit());
        assertArrayEquals(eager.points()[0], lazy.points()[0]);
        assertArrayEquals(eager.points()[1], lazy.points()[1]);
        assertArrayEquals(new int[]{2, 5}, VehicleRoutePlanner.snapToMaskOnDemand(
                index -> clearance.passableArray()[index], 14, 12, 2, 5, 3));
        assertFalse(covers(lazy.points(), 5, 5));
        assertFalse(covers(lazy.points(), 8, 5));
    }

    private static boolean covers(float[][] route, int cellX, int cellY) {
        for (int i = 1; i < route[0].length; i++) {
            float ax = route[0][i - 1], ay = route[1][i - 1];
            float dx = route[0][i] - ax, dy = route[1][i] - ay;
            int samples = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) / 0.1));
            for (int sample = 0; sample <= samples; sample++) {
                float t = sample / (float) samples;
                if ((int) Math.floor(ax + dx * t) == cellX
                        && (int) Math.floor(ay + dy * t) == cellY) return true;
            }
        }
        return false;
    }
}
