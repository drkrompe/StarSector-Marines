package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftBrushTest {

    /** A light wheeled hull, 3.0 by 1.5. */
    private static final VehicleType LIGHT = VehicleType.MILITIA_TECHNICAL;
    /** A medium hull, which may brush a tree's outer crown. */
    private static final VehicleType MEDIUM = VehicleType.HEAVY_APC;

    @Test
    void growthAndFencesAreSoftAndCrownsOnlyToAMediumHull() {
        NavigationGrid grid = open(20, 20);
        growth(grid, 2, 2);
        natural(grid, 4, 4);
        grid.setWalkable(6, 6, false);
        grid.setWalkable(8, 8, false);
        grid.setTag(8, 8, CellTag.CRUSHABLE, true);
        SoftBrush.Props props = props(6, 6, 4, 4);

        assertTrue(SoftBrush.soft(grid, 2, 2, LIGHT, props), "growth");
        assertTrue(SoftBrush.soft(grid, 6, 6, LIGHT, props), "fence");
        assertFalse(SoftBrush.soft(grid, 4, 4, LIGHT, props), "a light hull never brushes a crown");
        assertTrue(SoftBrush.soft(grid, 4, 4, MEDIUM, props), "outer crown");
        assertFalse(SoftBrush.soft(grid, 8, 8, LIGHT, props), "a lone boulder is crushable but hard");
        assertFalse(SoftBrush.soft(grid, 4, 4, MEDIUM, SoftBrush.NO_PROPS), "a trunk or a cliff is hard");
    }

    @Test
    void theFootprintReachesIntoSoftGroundByTheDepthAndNoFurther() {
        NavigationGrid grid = open(20, 20);
        growth(grid, 10, 10);
        NavigationGrid soft = new SoftBrush(SoftBrush.NO_PROPS).softView(grid, LIGHT);
        // Facing north, so the hull's right side is at x = centre + 0.75: reach into the growth cell by 0.2, then 0.4.
        assertFalse(VehicleFootprint.isPoseFeasible(9.45f, 10.5f, 0f, 3f, 1.5f, grid));
        assertTrue(fits(9.45f, grid, soft));
        assertFalse(fits(9.65f, grid, soft));
    }

    @Test
    void theFootprintNeverTouchesHardGroundWhateverTheDepth() {
        NavigationGrid grid = open(20, 20);
        grid.setWalkable(10, 10, false);
        NavigationGrid soft = new SoftBrush(SoftBrush.NO_PROPS).softView(grid, LIGHT);
        assertFalse(fits(9.3f, grid, soft));
        assertTrue(fits(9.25f, grid, soft), "touching a wall is not overlapping it");
    }

    @Test
    void aBrushingSweepStopsAtTheDepthInSoftGroundAndAtContactWithHardGround() {
        NavigationGrid grid = open(20, 20);
        for (int y = 8; y <= 12; y++) growth(grid, 10, y);
        for (int y = 8; y <= 12; y++) grid.setWalkable(5, y, false);
        SoftBrush brush = new SoftBrush(SoftBrush.NO_PROPS);

        VehicleTerrainMotion.Result into = brush.sweep(new Pose(9f, 10.5f, 0f), new Pose(9.8f, 10.5f, 0f),
                LIGHT, grid, false);
        assertTrue(into.blocked());
        assertEquals(9.55f, into.pose().x, .02f);

        VehicleTerrainMotion.Result wall = brush.sweep(new Pose(7.5f, 10.5f, 0f), new Pose(6.5f, 10.5f, 0f),
                LIGHT, grid, false);
        assertTrue(wall.blocked());
        assertEquals(6.75f, wall.pose().x, .02f);
    }

    @Test
    void theSoftViewIsKeptAndCatchesUpWithTheGround() {
        NavigationGrid grid = open(20, 20);
        SoftBrush brush = new SoftBrush(SoftBrush.NO_PROPS);
        NavigationGrid view = brush.softView(grid, LIGHT);
        growth(grid, 3, 3);
        grid.setWalkable(4, 4, false);

        assertSame(view, brush.softView(grid, LIGHT));
        assertTrue(view.isWalkable(3, 3), "new growth is soft");
        assertFalse(view.isWalkable(4, 4), "a new wall is hard");
    }

    @Test
    void routeProofAcceptsARouteWhoseFootprintOnlyGrazesSoftGround() {
        // Eastward along y = 10.5 the hull spans 9.75..11.25, so growth from y = 11 clips it by a quarter cell.
        NavigationGrid grid = open(20, 20);
        for (int x = 8; x <= 12; x++) growth(grid, x, 11);
        float[][] route = {{3f, 17f}, {10.5f, 10.5f}};
        NavigationGrid soft = new SoftBrush(SoftBrush.NO_PROPS).softView(grid, LIGHT);

        assertNull(TurnAwareCorridor.refine(route, LIGHT, grid, null, null).points());
        assertNotNull(TurnAwareCorridor.refine(route, LIGHT, grid, null, soft).points());
    }

    private static boolean fits(float x, NavigationGrid grid, NavigationGrid soft) {
        return SoftBrush.fits(x, 10.5f, 0f, 3f, 1.5f, 3f, 1.5f, grid, soft);
    }

    private static NavigationGrid open(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    private static void natural(NavigationGrid grid, int x, int y) {
        grid.setWalkable(x, y, false);
        grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
    }

    private static void growth(NavigationGrid grid, int x, int y) {
        natural(grid, x, y);
        grid.setTag(x, y, CellTag.CRUSHABLE, true);
    }

    private static SoftBrush.Props props(int fenceX, int fenceY, int crownX, int crownY) {
        return new SoftBrush.Props() {
            @Override public boolean fence(int x, int y) { return x == fenceX && y == fenceY; }
            @Override public boolean outerCrown(int x, int y) { return x == crownX && y == crownY; }
        };
    }
}
