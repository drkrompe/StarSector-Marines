package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualTerrainMotionTest {
    private static final float RADIUS = UnitType.MARINE.radius;
    private static final float TOLERANCE = 0.001f;

    @Test
    void openFloorAppliesTheRequestedDisplacementWithoutNormalizingIt() {
        NavigationGrid grid = floor(12, 8);
        var result = ManualTerrainMotion.move(grid, 1.5f, 2.5f, 7f, 2f, RADIUS);
        assertEquals(8.5f, result.x());
        assertEquals(4.5f, result.y());
        assertEquals(7f, result.dx());
        assertEquals(2f, result.dy());
    }

    @Test
    void longStepStopsAtTheFirstWallWithRoomForTheBody() {
        NavigationGrid grid = floor(12, 5);
        grid.setWalkable(4, 2, false);
        var result = ManualTerrainMotion.move(grid, 1.5f, 2.5f, 9f, 0f, RADIUS);
        assertEquals(4f - RADIUS, result.x(), TOLERANCE);
        assertEquals(2.5f, result.y());
        assertTrue(result.x() < 4f - RADIUS);
    }

    @Test
    void blockedReciprocalHalfEdgeStopsBothDirectionsBetweenWalkableCells() {
        NavigationGrid grid = floor(8, 5);
        grid.blockEdge(4, 2, Direction.W);
        var east = ManualTerrainMotion.move(grid, 1.5f, 2.5f, 5f, 0f, RADIUS);
        var west = ManualTerrainMotion.move(grid, 6.5f, 2.5f, -5f, 0f, RADIUS);
        assertEquals(4f - RADIUS, east.x(), TOLERANCE);
        assertEquals(4f + RADIUS, west.x(), TOLERANCE);
    }

    @Test
    void wallTangentRetainsUnusedParallelMovement() {
        NavigationGrid grid = floor(8, 8);
        for (int y = 0; y < 8; y++) grid.setWalkable(4, y, false);
        var result = ManualTerrainMotion.move(grid, 2.5f, 2.5f, 3f, 2f, RADIUS);
        assertEquals(4f - RADIUS, result.x(), TOLERANCE);
        assertEquals(4.5f, result.y(), TOLERANCE);
        // Holding into the wall next tick neither creeps through nor banks motion.
        var held = ManualTerrainMotion.move(grid, result.x(), result.y(), 3f, 0f, RADIUS);
        assertEquals(result.x(), held.x(), TOLERANCE);
    }

    @Test
    void aFloatRoundedTangentCanSlideButCannotMoveFurtherIntoTheWall() {
        NavigationGrid grid = floor(8, 8);
        for (int y = 0; y < 8; y++) grid.setWalkable(4, y, false);
        float touchingX = 4f - RADIUS;
        var result = ManualTerrainMotion.move(grid, touchingX, 2.5f, 1f, 1f, RADIUS);
        assertEquals(touchingX, result.x(), TOLERANCE);
        assertEquals(3.5f, result.y(), TOLERANCE);
    }

    @Test
    void diagonalCannotCutBetweenTouchingSolidCorners() {
        NavigationGrid grid = floor(6, 6);
        grid.setWalkable(3, 2, false);
        grid.setWalkable(2, 3, false);
        var result = ManualTerrainMotion.move(grid, 2.5f, 2.5f, 2f, 2f, RADIUS);
        assertTrue(result.x() <= 3f - RADIUS);
        assertTrue(result.y() <= 3f - RADIUS);
    }

    @Test
    void closedDiagonalBitUsesTheNavigationStepContract() {
        NavigationGrid grid = floor(6, 6);
        grid.blockEdge(2, 2, Direction.NE);
        var result = ManualTerrainMotion.move(grid, 2.5f, 2.5f, 1f, 1f, RADIUS);
        assertTrue(result.x() < 3f);
        assertTrue(result.y() < 3f);
    }

    @Test
    void roundedCornerContactUsesTheCircleRatherThanAnExpandedSquare() {
        NavigationGrid grid = floor(8, 8);
        grid.setWalkable(4, 4, false);
        // The line misses the rounded corner while crossing its bounding box.
        var result = ManualTerrainMotion.move(grid, 3.72f, 3.71f, 0.1f, 0f, RADIUS);
        assertEquals(3.82f, result.x(), TOLERANCE);
        assertEquals(3.71f, result.y(), TOLERANCE);
    }

    @Test
    void thinBarrierEndpointHasCircularClearance() {
        NavigationGrid grid = floor(8, 8);
        grid.blockSharedEdge(3, 3, Direction.E);
        var result = ManualTerrainMotion.move(grid, 3.5f, 4.2f, 1f, 0f, RADIUS);
        assertTrue(result.y() > 4.2f, "contact with the end cap should slide away from it");
        assertTrue(Math.hypot(result.x() - 4f, result.y() - 4f) >= RADIUS - TOLERANCE);
    }

    @Test
    void mapEdgesStopTheWholeBodyAndAllowSliding() {
        NavigationGrid grid = floor(8, 8);
        var result = ManualTerrainMotion.move(grid, 1.5f, 1.5f, -9f, 2f, RADIUS);
        assertEquals(RADIUS, result.x(), TOLERANCE);
        assertEquals(3.5f, result.y(), TOLERANCE);
        var corner = ManualTerrainMotion.move(grid, 6.5f, 6.5f, 9f, 9f, RADIUS);
        assertEquals(8f - RADIUS, corner.x(), TOLERANCE);
        assertEquals(8f - RADIUS, corner.y(), TOLERANCE);
    }

    @Test
    void changedTopologyIsReadOnTheNextCall() {
        NavigationGrid grid = floor(8, 5);
        grid.blockSharedEdge(3, 2, Direction.E);
        var blocked = ManualTerrainMotion.move(grid, 2.5f, 2.5f, 3f, 0f, RADIUS);
        assertEquals(4f - RADIUS, blocked.x(), TOLERANCE);
        grid.openSharedEdge(3, 2, Direction.E);
        var open = ManualTerrainMotion.move(grid, blocked.x(), blocked.y(), 1f, 0f, RADIUS);
        assertEquals(blocked.x() + 1f, open.x(), TOLERANCE);
        grid.setWalkable(5, 2, false);
        var wall = ManualTerrainMotion.move(grid, 4.5f, 2.5f, 2f, 0f, RADIUS);
        assertEquals(5f - RADIUS, wall.x(), TOLERANCE);
        grid.setWalkableFloor(5, 2);
        var demolished = ManualTerrainMotion.move(grid, wall.x(), wall.y(), 1f, 0f, RADIUS);
        assertEquals(wall.x() + 1f, demolished.x(), TOLERANCE);
    }

    @Test
    void walkableCoverDoesNotBecomeMovementTerrain() {
        NavigationGrid grid = floor(8, 5);
        grid.setCoverAtFacing(3, 2, 0, 3);
        var result = ManualTerrainMotion.move(grid, 1.5f, 2.5f, 5f, 0f, RADIUS);
        assertEquals(6.5f, result.x());
    }

    @Test
    void illegalPlacementDoesNotTeleportToEscapeTerrain() {
        NavigationGrid grid = floor(8, 5);
        grid.setWalkable(3, 2, false);
        for (float radius : new float[]{RADIUS, 0.00001f}) {
            var result = ManualTerrainMotion.move(grid, 3.5f, 2.5f, 1f, 0f, radius);
            assertEquals(3.5f, result.x());
            assertEquals(0f, result.dx());
        }
    }

    @Test
    void straightSweepDoesNotTreatSlidingAsAClearRouteSegment() {
        NavigationGrid grid = floor(8, 8);
        grid.setWalkable(4, 4, false);
        assertFalse(ManualTerrainMotion.canSweepStraight(grid, 2.5f, 3.5f, 3f, 1f, 0.6f));
        assertTrue(ManualTerrainMotion.canSweepStraight(grid, 2.5f, 2.5f, 3f, 0f, 0.6f));
        assertFalse(ManualTerrainMotion.canSweepStraight(grid, 2.5f, 2.5f, -3f, 0f, 0.6f));
    }

    private static NavigationGrid floor(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
