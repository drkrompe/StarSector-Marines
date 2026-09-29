package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectControlAimLaneTest {
    static NavigationGrid open() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int x = 0; x < 12; x++) for (int y = 0; y < 12; y++) grid.setWalkable(x, y, true);
        return grid;
    }
    @Test void clearLaneAndSmokeHaveNoStop() {
        var grid = open();
        grid.addTransientOpacityAt(grid.index(4, 3));
        var lane = DirectControlAimLane.trace(grid, 1.5f, 3.5f, 9f, 3.5f);
        assertFalse(lane.blocked());
        assertEquals(1, DirectControlAimLane.strokes(lane, 20, 0, 0).size());
    }
    @Test void wallMarkerIsAtNearBoundaryFromEitherSide() {
        var grid = open(); grid.setWalkable(5, 3, false);
        assertEquals(5f, DirectControlAimLane.trace(grid, 1.5f, 3.5f, 9f, 3.5f).stopX());
        assertEquals(6f, DirectControlAimLane.trace(grid, 9f, 3.5f, 1.5f, 3.5f).stopX());
    }
    @Test void exactCornerDoesNotInventAnOrthogonalStop() {
        var grid = open(); grid.setWalkable(3, 2, false); grid.setWalkable(2, 3, false);
        assertFalse(DirectControlAimLane.trace(grid, 2.5f, 2.5f, 7.5f, 7.5f).blocked());
        grid.setWalkable(3, 3, false);
        var lane = DirectControlAimLane.trace(grid, 2.5f, 2.5f, 7.5f, 7.5f);
        assertEquals(3f, lane.stopX()); assertEquals(3f, lane.stopY());
    }
    @Test void boundaryEndpointAndSameCellTerminate() {
        var grid = open(); grid.setWalkable(5, 3, false);
        assertEquals(1f, DirectControlAimLane.trace(grid, 1.5f, 3.5f, 5f, 3.5f).stopFraction());
        assertFalse(DirectControlAimLane.trace(grid, 1.1f, 1.2f, 1.7f, 1.8f).blocked());
    }
    @Test void fogCapsBothStylesBeforeHiddenGeometryAndPreservesVisibleStop() {
        var lane = new DirectControlAimLane.Lane(1.5f, 3.5f, 9.5f, 3.5f, .5f);
        var capped = DirectControlAimLane.visiblePrefix(lane, (x, y) -> x < 4);
        assertNotNull(capped); assertFalse(capped.blocked()); assertTrue(capped.aimX() < 4f);
        var visibleStop = DirectControlAimLane.visiblePrefix(lane, (x, y) -> x < 8);
        assertTrue(visibleStop.blocked()); assertTrue(visibleStop.aimX() < 8f);
        assertNull(DirectControlAimLane.visiblePrefix(lane, (x, y) -> false));
    }
    @Test void visibilityWalkUsesExactCornerAndBoundaryEndpoints() {
        var lane = new DirectControlAimLane.Lane(2.5f, 2.5f, 7f, 7f, Float.NaN);
        var prefix = DirectControlAimLane.visiblePrefix(lane, (x, y) -> x == y && x < 5);
        assertNotNull(prefix); assertTrue(prefix.aimX() < 5f); assertEquals(prefix.aimX(), prefix.aimY());
    }
    @Test void offAxisBarrelGuardCannotExposeUnrevealedTerrain() {
        var lane = new DirectControlAimLane.Lane(2.5f, 3.5f, 9.5f, 3.5f, 0f, 4.5f, 4.5f);
        var prefix = DirectControlAimLane.visiblePrefix(lane, (x, y) -> y < 4);
        assertNotNull(prefix); assertFalse(prefix.blocked()); assertTrue(prefix.aimY() < 4f);
    }
    @Test void westwardBoundaryCannotExposeHiddenBlockerThroughVisibleAdjacentCell() {
        var grid = open(); grid.setWalkable(5, 3, false);
        var lane = DirectControlAimLane.trace(grid, 9.5f, 3.5f, 1.5f, 3.5f);
        assertFalse(DirectControlAimLane.visiblePrefix(lane, (x, y) -> x >= 6).blocked());
    }
    @Test void unseenMuzzleSuppressesGuideRegardlessOfHiddenGuardPresence() {
        var grid = open();
        var origin = new DirectControlAimOrigins.Origin(6.5f, 3.5f, 2.5f, 3.5f);
        assertNull(DirectControlAimLane.observed(grid, origin, 9.5f, 3.5f, (x, y) -> x < 4));
        grid.setWalkable(5, 3, false);
        assertNull(DirectControlAimLane.observed(grid, origin, 9.5f, 3.5f, (x, y) -> x < 4));
    }
    @Test void barrelGuardMarkerRetainsItsActualPosition() {
        var lane = new DirectControlAimLane.Lane(6f, 4f, 10f, 8f, 0f, 5f, 3.5f);
        var strokes = DirectControlAimLane.strokes(lane, 20f, 10f, 30f);
        assertEquals(110f, strokes.get(0).endX()); assertEquals(100f, strokes.get(0).endY());
        assertTrue(strokes.size() > 3);
    }
}
