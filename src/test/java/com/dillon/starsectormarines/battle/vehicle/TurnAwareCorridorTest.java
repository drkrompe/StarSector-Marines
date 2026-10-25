package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** Too narrow for the APC's fillet, and for its footprint to swing round on the spot. */
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

    @Test
    void measuresABendsLegsToTheCornersEitherSideNotToAVertexInTheStraight() {
        NavigationGrid grid = new NavigationGrid(60, 60);
        carve(grid, 0, 0, 59, 59);

        // A long diagonal with a vertex one cell short of its bend, as a string-pull leaves one.
        TurnAwareCorridor.Result result = TurnAwareCorridor.refine(
                new float[][]{
                        new float[]{10.5f, 29.5f, 30.5f, 30.5f},
                        new float[]{10.5f, 29.5f, 30.5f, 52.5f}
                }, VehicleType.BREAKTHROUGH_TANK, grid);

        assertNotNull(result.points(), "failed at " + result.failedX() + "," + result.failedY());
    }

    @Test
    void pivotsAtABendNoFilletFitsWhereTheTankCanTurnOnTheSpot() {
        NavigationGrid grid = new NavigationGrid(60, 60);
        // A crossroads of two five-cell rides, as The Burn cuts them: room to turn on the spot at the middle, none for
        // the turning circle.
        carve(grid, 28, 0, 32, 59);
        carve(grid, 0, 28, 59, 32);

        float[][] route = {new float[]{30.5f, 30.5f, 22.5f}, new float[]{50.5f, 30.5f, 30.5f}};
        TurnAwareCorridor.Result result = TurnAwareCorridor.refine(route, VehicleType.BREAKTHROUGH_TANK, grid);

        assertNotNull(result.points(), "failed at " + result.failedX() + "," + result.failedY());
        assertEquals(3, result.points()[0].length, "the pivot bend stays a sharp vertex");
        assertEquals(30.5f, result.points()[0][1], 1e-4f);
        assertEquals(30.5f, result.points()[1][1], 1e-4f);
    }

    @Test
    void neverPlansAPivotForAWheeledHull() {
        NavigationGrid grid = new NavigationGrid(60, 60);
        carve(grid, 0, 0, 59, 59);
        // Legs two cells long: no fillet fits, and open ground all round to turn on the spot.
        float[][] route = {new float[]{30.5f, 30.5f, 28.5f}, new float[]{32.5f, 30.5f, 30.5f}};

        assertNotNull(TurnAwareCorridor.refine(route, VehicleType.BREAKTHROUGH_TANK, grid).points());
        assertNull(TurnAwareCorridor.refine(route, VehicleType.HEAVY_APC, grid).points(),
                "a truck spinning in place is its last resort, not a route");
    }

    @Test
    void stillFilletsABendTheTurningCircleFitsRatherThanPivoting() {
        NavigationGrid grid = new NavigationGrid(60, 60);
        carve(grid, 0, 0, 59, 59);

        TurnAwareCorridor.Result result = TurnAwareCorridor.refine(
                new float[][]{new float[]{30.5f, 30.5f, 10.5f}, new float[]{55.5f, 30.5f, 30.5f}},
                VehicleType.BREAKTHROUGH_TANK, grid);

        assertNotNull(result.points());
        assertTrue(result.points()[0].length > 3, "open ground keeps the arc");
    }

    @Test
    void anOrderIsNotRefusedForTheMarginAroundWhereTheHullAlreadyStands() {
        NavigationGrid grid = new NavigationGrid(30, 40);
        carve(grid, 0, 0, 29, 39);
        // A wall just behind the hull's tail: the exact footprint clears it, the sampling margin does not.
        for (int x = 8; x <= 12; x++) grid.setWalkable(x, 8, false);
        float[][] route = {new float[]{10.5f, 10.5f}, new float[]{10.4f, 30.5f}};

        assertNull(TurnAwareCorridor.refine(route, VehicleType.HEAVY_APC, grid).points());
        assertNotNull(TurnAwareCorridor.refine(route, VehicleType.HEAVY_APC, grid,
                new Pose(10.5f, 10.4f, 0f)).points());
    }

    @Test
    void anOrderEndingWhereTheMarginClipsArrivesALittleShort() {
        NavigationGrid grid = new NavigationGrid(30, 40);
        carve(grid, 0, 0, 29, 39);
        // A wall just past the end: the hull can stand at the end exactly, but not with the margin.
        for (int x = 8; x <= 12; x++) grid.setWalkable(x, 22, false);
        float[][] route = {new float[]{10.5f, 10.5f}, new float[]{5.5f, 20.6f}};

        assertNull(TurnAwareCorridor.refine(route, VehicleType.HEAVY_APC, grid).points());
        float[][] points = TurnAwareCorridor.refine(route, VehicleType.HEAVY_APC, grid,
                new Pose(10.5f, 5.5f, 0f)).points();
        assertNotNull(points);
        float endY = points[1][points[1].length - 1];
        assertTrue(endY < 20.6f && endY > 18f, "pulled back a little, to y=" + endY);
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
