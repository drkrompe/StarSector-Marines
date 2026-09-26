package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClearanceRoutePlannerTest {
    private final ClearanceRoutePlanner planner = new ClearanceRoutePlanner();

    @Test
    void actualRadiusRefusesBulwarkButAdmitsSmallerBodiesThroughOneCellPassage() {
        NavigationGrid grid = new NavigationGrid(12, 8);
        floor(grid, 1, 1, 4, 7);
        floor(grid, 8, 1, 11, 7);
        floor(grid, 4, 3, 8, 4);
        assertEquals(ClearanceRoutePlanner.Status.UNREACHABLE,
                route(grid, 2f, 3.5f, 9f, 3.5f, 0.6f, 0f).status());
        for (float radius : new float[]{0.5f, 0.48f}) {
            var found = route(grid, 2f, 3.5f, 9f, 3.5f, radius, 0f);
            assertEquals(ClearanceRoutePlanner.Status.FOUND, found.status());
            assertLegal(grid, found.waypoints(), radius);
        }
    }

    @Test
    void twoCellCorridorUsesItsLegalCenterlineAndKeepsRequestedDestination() {
        NavigationGrid grid = new NavigationGrid(12, 8);
        floor(grid, 1, 3, 11, 5);
        var found = route(grid, 2f, 4f, 9.5f, 3.5f, 0.6f, 0.5f);
        assertEquals(ClearanceRoutePlanner.Status.FOUND, found.status());
        assertEquals(new ClearanceRoutePlanner.Point(9.5f, 3.5f), found.requested());
        assertEquals(new ClearanceRoutePlanner.Point(9.5f, 4f), found.resolved());
        assertTrue(found.waypoints().stream().allMatch(p -> p.y() == 4f));
        assertLegal(grid, found.waypoints(), 0.6f);
        assertEquals(ClearanceRoutePlanner.Status.NO_LEGAL_DESTINATION,
                route(grid, 2f, 4f, 9.5f, 3.5f, 0.6f, 0f).status());
    }

    @Test
    void twoCellLTurnSweepsEverySegmentAroundTheInsideCorner() {
        NavigationGrid grid = new NavigationGrid(10, 11);
        floor(grid, 1, 1, 8, 3);
        floor(grid, 6, 1, 8, 10);
        var found = route(grid, 2f, 2f, 7f, 8f, 0.6f, 0f);
        assertEquals(ClearanceRoutePlanner.Status.FOUND, found.status());
        assertFalse(ManualTerrainMotion.canSweepStraight(grid, 2f, 2f, 5f, 6f, 0.6f));
        assertLegal(grid, found.waypoints(), 0.6f);
    }

    @Test
    void exactSubcellStartIsAttachedWithoutMovingOrReplacingIt() {
        NavigationGrid grid = new NavigationGrid(12, 8);
        floor(grid, 1, 3, 11, 5);
        var found = route(grid, 2.23f, 3.91f, 9.5f, 4f, 0.6f, 0f);
        assertEquals(ClearanceRoutePlanner.Status.FOUND, found.status());
        assertEquals(new ClearanceRoutePlanner.Point(2.23f, 3.91f), found.waypoints().get(0));
        assertLegal(grid, found.waypoints(), 0.6f);
    }

    @Test
    void reciprocalEdgesAndTheirCurrentRevisionAreUsedOnEveryRequest() {
        NavigationGrid grid = new NavigationGrid(8, 6);
        floor(grid, 0, 0, 8, 6);
        for (int y = 0; y < 6; y++) grid.blockEdge(4, y, Direction.W);
        var blocked = route(grid, 1.5f, 3f, 6.5f, 3f, 0.6f, 0f);
        assertEquals(ClearanceRoutePlanner.Status.UNREACHABLE, blocked.status());
        grid.openSharedEdge(3, 2, Direction.E);
        grid.openSharedEdge(3, 3, Direction.E);
        var opened = route(grid, 1.5f, 3f, 6.5f, 3f, 0.6f, 0f);
        assertEquals(ClearanceRoutePlanner.Status.FOUND, opened.status());
        assertTrue(opened.topologyRevision() > blocked.topologyRevision());
        assertLegal(grid, opened.waypoints(), 0.6f);
        grid.blockEdge(4, 2, Direction.W);
        assertEquals(ClearanceRoutePlanner.Status.UNREACHABLE,
                route(grid, 1.5f, 3f, 6.5f, 3f, 0.6f, 0f).status());
    }

    @Test
    void mapBoundaryUsesWholeBodyAndInvalidStartNeverSnaps() {
        NavigationGrid grid = new NavigationGrid(8, 6);
        floor(grid, 0, 0, 8, 6);
        var invalid = route(grid, 0.3f, 2f, 6f, 2f, 0.6f, 0f);
        assertEquals(ClearanceRoutePlanner.Status.INVALID_START, invalid.status());
        assertTrue(invalid.waypoints().isEmpty());
        assertNull(invalid.resolved());
        assertEquals(ClearanceRoutePlanner.Status.NO_LEGAL_DESTINATION,
                route(grid, 3f, 2f, 0f, 2f, 0.6f, 0.5f).status());
        var nearBoundary = route(grid, 3f, 2f, 0f, 2f, 0.6f, 1f);
        assertEquals(new ClearanceRoutePlanner.Point(1f, 2f), nearBoundary.resolved());
        assertLegal(grid, nearBoundary.waypoints(), 0.6f);
    }

    @Test
    void searchLimitIsExplicitAndCannotMasqueradeAsAFartherResolvedGoal() {
        NavigationGrid grid = new NavigationGrid(20, 12);
        floor(grid, 0, 0, 20, 12);
        for (int y = 0; y < 12; y++) grid.blockSharedEdge(9, y, Direction.E);
        var limited = planner.findRoute(grid, 2f, 6f, 11f, 6f, 0.6f, 3f, 4);
        assertEquals(ClearanceRoutePlanner.Status.SEARCH_LIMIT, limited.status());
        assertEquals(4, limited.expandedNodes());
        assertTrue(limited.clearanceChecks() > 0);
        assertNull(limited.resolved());
        assertTrue(limited.waypoints().isEmpty());
    }

    @Test
    void clearRouteUsesAStraightProofWithoutSpendingSearchBudget() {
        NavigationGrid grid = new NavigationGrid(40, 24);
        floor(grid, 0, 0, 40, 24);
        var found = planner.findRoute(grid, 1.23f, 1.71f,
                35f, 20f, 0.6f, 0f, 1);
        assertEquals(ClearanceRoutePlanner.Status.FOUND, found.status());
        assertEquals(List.of(new ClearanceRoutePlanner.Point(1.23f, 1.71f),
                new ClearanceRoutePlanner.Point(35f, 20f)), found.waypoints());
        assertEquals(0, found.expandedNodes());
        assertEquals(3, found.clearanceChecks(), "start, destination and complete straight sweep");
        assertLegal(grid, found.waypoints(), 0.6f);
    }

    @Test
    void repeatedQueriesHaveDeterministicRoutesAndCounters() {
        NavigationGrid grid = new NavigationGrid(12, 8);
        floor(grid, 0, 0, 12, 8);
        grid.setWalkable(6, 4, false);
        var first = route(grid, 2f, 4f, 9f, 4f, 0.6f, 0f);
        assertEquals(first, route(grid, 2f, 4f, 9f, 4f, 0.6f, 0f));
    }

    private ClearanceRoutePlanner.Result route(NavigationGrid grid, float x, float y,
                                                float targetX, float targetY, float radius,
                                                float tolerance) {
        return planner.findRoute(grid, x, y, targetX, targetY, radius, tolerance, 4000);
    }

    private static void assertLegal(NavigationGrid grid, List<ClearanceRoutePlanner.Point> points,
                                    float radius) {
        assertFalse(points.isEmpty());
        for (int i = 0; i < points.size(); i++) {
            var point = points.get(i);
            assertTrue(ManualTerrainMotion.canStand(grid, point.x(), point.y(), radius));
            if (i > 0) {
                var from = points.get(i - 1);
                assertTrue(ManualTerrainMotion.canSweepStraight(grid, from.x(), from.y(),
                        point.x() - from.x(), point.y() - from.y(), radius));
            }
        }
    }

    private static void floor(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) grid.setWalkableFloor(x, y);
        }
    }
}
