package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnAwareCorridorTest {

    @Test
    void roundsAWideRightAngleWithFootprintSafeMinimumRadiusPoses() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        carve(grid, 5, 0, 25, 25);
        carve(grid, 5, 5, 39, 25);

        TurnAwareCorridor.Result result = TurnAwareCorridor.refine(
                new float[][]{
                        new float[]{15.5f, 15.5f, 32.5f},
                        new float[]{3.5f, 15.5f, 15.5f}
                }, VehicleType.HEAVY_APC, grid);

        assertNotNull(result.points());
        assertTrue(result.points()[0].length > 3, "the sharp vertex should become a sampled arc");
        assertAllSegmentsFeasible(result.points(), grid);
    }

    @Test
    void rejectsAThreeCellElbowThatOnlyFitsTheStaticWidthMask() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        carve(grid, 10, 0, 12, 12);
        carve(grid, 10, 10, 25, 12);

        TurnAwareCorridor.Result result = TurnAwareCorridor.refine(
                new float[][]{
                        new float[]{11.5f, 11.5f, 24.5f},
                        new float[]{3.5f, 11.5f, 11.5f}
                }, VehicleType.HEAVY_APC, grid);

        assertNull(result.points());
        assertTrue(result.failedX() >= 10f && result.failedX() <= 13f);
        assertTrue(result.failedY() >= 8f && result.failedY() <= 13f,
                "failure should be on the elbow approach, was y=" + result.failedY());
    }

    private static void assertAllSegmentsFeasible(float[][] path, NavigationGrid grid) {
        float[] xs = path[0], ys = path[1];
        for (int i = 1; i < xs.length; i++) {
            float dx = xs[i] - xs[i - 1], dy = ys[i] - ys[i - 1];
            float length = (float) Math.hypot(dx, dy);
            float facing = AirBody.facingToward(dx, dy);
            for (float d = 0f; d <= length; d += 0.25f) {
                float t = length > 1e-5f ? Math.min(1f, d / length) : 0f;
                float x = xs[i - 1] + dx * t;
                float y = ys[i - 1] + dy * t;
                assertTrue(VehicleFootprint.isPoseFeasible(x, y, facing,
                        VehicleType.HEAVY_APC.visualLengthCells,
                        VehicleType.HEAVY_APC.visualWidthCells, grid),
                        "infeasible sampled pose at " + x + "," + y + " @ " + facing);
            }
        }
    }

    private static void carve(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) grid.setWalkableFloor(x, y);
        }
    }
}
