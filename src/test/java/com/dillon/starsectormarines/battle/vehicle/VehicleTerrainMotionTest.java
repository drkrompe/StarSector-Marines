package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleTerrainMotionTest {
    private static final float LENGTH = 2.4f, WIDTH = 1.4f;

    @Test
    void exactPoseRejectsClosedEdgesEvenWhenEveryCellIsWalkable() {
        NavigationGrid grid = floor();
        assertTrue(VehicleFootprint.isPoseFeasible(6f, 6f, 0f, LENGTH, WIDTH, grid));
        grid.blockSharedEdge(5, 5, Direction.E);
        assertFalse(VehicleFootprint.isPoseFeasible(6f, 6f, 0f, LENGTH, WIDTH, grid));
        grid.openSharedEdge(5, 5, Direction.E);
        assertTrue(VehicleFootprint.isPoseFeasible(6f, 6f, 0f, LENGTH, WIDTH, grid));
    }

    @Test
    void legalTurnEndpointsCannotHideAnIntermediateCornerOrThinEdgeCollision() {
        for (boolean edge : new boolean[]{false, true}) {
            NavigationGrid grid = floor();
            if (edge) grid.blockSharedEdge(6, 7, Direction.E);
            else grid.setWalkable(7, 7, false);
            Pose from = new Pose(6.2f, 6.2f, 0f), to = new Pose(6.2f, 6.2f, -90f);
            assertTrue(legal(from, grid));
            assertTrue(legal(to, grid));
            assertFalse(legal(new Pose(6.2f, 6.2f, -45f), grid));
            var result = VehicleTerrainMotion.sweep(from, to, LENGTH, WIDTH, grid, false);
            assertTrue(result.blocked());
            assertTrue(result.fraction() > 0f && result.fraction() < .5f);
            assertTrue(legal(result.pose(), grid));
            assertFalse(VehicleTerrainMotion.isSweepFeasible(from, to, LENGTH, WIDTH, grid));
        }
    }

    @Test
    void longTranslationStopsAtFirstClosedEdgeAndUsesOpenedTopologyImmediately() {
        NavigationGrid grid = floor();
        for (int y = 0; y < 12; y++) grid.blockSharedEdge(5, y, Direction.E);
        Pose from = new Pose(2.5f, 5.5f, -90f), to = new Pose(9.5f, 5.5f, -90f);
        var blocked = VehicleTerrainMotion.sweep(from, to, LENGTH, WIDTH, grid, false);
        assertTrue(blocked.blocked());
        assertTrue(blocked.pose().x > 4.7f && blocked.pose().x <= 4.8f);
        assertTrue(legal(blocked.pose(), grid));
        for (int y = 0; y < 12; y++) grid.openSharedEdge(5, y, Direction.E);
        var opened = VehicleTerrainMotion.sweep(blocked.pose(), to, LENGTH, WIDTH, grid, false);
        assertFalse(opened.blocked());
        assertEquals(to.x, opened.pose().x);
        grid.setWalkable(6, 5, false);
        assertTrue(VehicleTerrainMotion.sweep(from, to, LENGTH, WIDTH, grid, false).blocked());
    }

    @Test
    void deliveryBoundsExceptionNeverExemptsAnOnMapObstacle() {
        NavigationGrid grid = floor();
        Pose from = new Pose(9.5f, 5.5f, -90f), to = new Pose(15f, 5.5f, -90f);
        var bounded = VehicleTerrainMotion.sweep(from, to, LENGTH, WIDTH, grid, false);
        assertTrue(bounded.blocked());
        assertTrue(bounded.pose().x <= 12f - LENGTH * .5f);
        assertFalse(VehicleTerrainMotion.sweep(from, to, LENGTH, WIDTH, grid, true).blocked());
        grid.setWalkable(11, 5, false);
        assertTrue(VehicleTerrainMotion.sweep(from, to, LENGTH, WIDTH, grid, true).blocked());
    }

    @Test
    void dockingChecksTheActualCurvedSweepAndChangedTerrain() {
        NavigationGrid grid = floor();
        Pose origin = new Pose(5f, 3f, 0f);
        ReedsShepp.Path path = new ReedsShepp.Path(List.of(
                new ReedsShepp.Element(ReedsShepp.Type.RIGHT, true, (float) Math.PI / 2f)));
        float radius = 3f, distance = path.lengthCells(radius);
        assertTrue(VehicleTerrainMotion.isReedsSheppFeasible(origin, path, radius, 0f, distance,
                LENGTH, WIDTH, grid));
        Pose middle = ReedsShepp.sample(origin, radius, path, distance * .5f);
        grid.setWalkable((int) middle.x, (int) middle.y, false);
        assertFalse(VehicleTerrainMotion.isReedsSheppFeasible(origin, path, radius, 0f, distance,
                LENGTH, WIDTH, grid));
        var result = VehicleTerrainMotion.sweepReedsShepp(origin, path, radius, 0f, distance,
                LENGTH, WIDTH, grid);
        assertTrue(result.blocked());
        assertTrue(legal(result.pose(), grid));
    }

    private static boolean legal(Pose pose, NavigationGrid grid) {
        return VehicleFootprint.isPoseFeasible(pose.x, pose.y, pose.facingDeg, LENGTH, WIDTH, grid);
    }

    private static NavigationGrid floor() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
