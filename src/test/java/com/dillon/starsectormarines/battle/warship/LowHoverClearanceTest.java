package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowHoverClearanceTest {
    private NavigationGrid open() {
        NavigationGrid grid = new NavigationGrid(80,60);
        for(int y=0;y<60;y++)for(int x=0;x<80;x++)grid.setWalkableFloor(x,y);
        return grid;
    }

    @Test void fullRectangleRejectsObstacleAwayFromTheCenterAndMapEdge() {
        NavigationGrid grid = open();
        assertTrue(LowHoverClearance.clear(grid,30,30,24,20));
        grid.setWalkable(19,21,false);
        assertFalse(LowHoverClearance.clear(grid,30,30,24,20));
        assertFalse(LowHoverClearance.clear(grid,11,30,24,20));
    }

    @Test void sweepCannotJumpAcrossAnObstacleBetweenClearEndpoints() {
        NavigationGrid grid = open();
        grid.setWalkable(40,30,false);
        assertTrue(LowHoverClearance.clear(grid,20,30,24,20));
        assertTrue(LowHoverClearance.clear(grid,60,30,24,20));
        assertFalse(LowHoverClearance.sweptClear(grid,20,30,60,30,24,20));
    }
}
