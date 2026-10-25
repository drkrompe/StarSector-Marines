package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NeutralPivotTest {

    private static final float LENGTH = 2.4f, WIDTH = 1.4f;

    @Test
    void turnsTheShorterWayOnOpenGround() {
        NavigationGrid grid = open(20, 20);
        assertEquals(1, NeutralPivot.clearDirection(10f, 10f, 0f, 90f, LENGTH, WIDTH, grid));
        assertEquals(-1, NeutralPivot.clearDirection(10f, 10f, 0f, -90f, LENGTH, WIDTH, grid));
    }

    @Test
    void refusesATurnWhoseSweepClipsTheGroundThoughBothEndsAreClear() {
        // A long, thin hull turning north to west swings its nose through the cell up and to its left, which neither
        // the north-facing nor the west-facing pose touches.
        NavigationGrid grid = open(20, 20, 9, 11);
        assertEquals(0, NeutralPivot.clearDirection(10.5f, 10.5f, 0f, 90f, 4f, .5f, grid));
        assertEquals(-1, NeutralPivot.clearDirection(10.5f, 10.5f, 0f, -90f, 4f, .5f, grid));
    }

    @Test
    void cannotTurnInACorridorNarrowerThanTheHullIsLong() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 9; x <= 10; x++) grid.setWalkableFloor(x, y);
        assertEquals(0, NeutralPivot.clearDirection(10f, 10f, 0f, 90f, LENGTH, WIDTH, grid));
    }

    private static NavigationGrid open(int w, int h) {
        return open(w, h, -1, -1);
    }

    /** Open ground but for one solid cell at ({@code solidX}, {@code solidY}). */
    private static NavigationGrid open(int w, int h, int solidX, int solidY) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) if (x != solidX || y != solidY) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
