package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavigationGridCoverHeightTest {

    private static final float EPS = 1e-6f;

    @Test
    void coverLevelAndCatchHeightStayPairedPerFacing() {
        NavigationGrid grid = new NavigationGrid(4, 4);

        grid.setCoverAtFacing(2, 2, NavigationGrid.FACING_W, 2);
        assertEquals(2, grid.getCoverAtFacing(2, 2, NavigationGrid.FACING_W));
        assertEquals(NavigationGrid.DEFAULT_COVER_CATCH_HALF_HEIGHT,
                grid.getCoverCatchHalfHeightAtFacing(2, 2, NavigationGrid.FACING_W), EPS);

        grid.setCoverAtFacing(2, 2, NavigationGrid.FACING_W, 3, 0.72f);
        assertEquals(3, grid.getCoverAt(2, 2, -1, 0));
        assertEquals(0.72f, grid.getCoverCatchHalfHeight(2, 2, -1, 0), EPS);

        grid.setCoverAtFacing(2, 2, NavigationGrid.FACING_W, 0, 9f);
        assertEquals(0f, grid.getCoverCatchHalfHeightAtFacing(
                2, 2, NavigationGrid.FACING_W), EPS);

        grid.setCoverAtFacing(2, 2, NavigationGrid.FACING_N, 1, 0.61f);
        grid.clear();
        assertEquals(0, grid.getCoverAtFacing(2, 2, NavigationGrid.FACING_N));
        assertEquals(0f, grid.getCoverCatchHalfHeightAtFacing(
                2, 2, NavigationGrid.FACING_N), EPS);
    }
}
