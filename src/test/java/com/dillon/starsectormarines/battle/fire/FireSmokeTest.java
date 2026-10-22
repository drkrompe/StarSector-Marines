package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireSmokeTest {

    private static NavigationGrid open() {
        NavigationGrid grid = new NavigationGrid(9, 5);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 9; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    @Test
    void burningBrushScreensUntilItBurnsOut() {
        NavigationGrid grid = open();
        FireSmoke smoke = new FireSmoke(grid);
        int idx = grid.index(4, 2);
        smoke.ignited(idx, FireFuel.BRUSH.bit | FireFuel.GRASS.bit, 0f, 0f, 0f);
        assertTrue(grid.hasTransientOpacityOnLine(0, 2, 8, 2));
        smoke.burntOut(idx);
        assertFalse(grid.hasTransientOpacityOnLine(0, 2, 8, 2));
    }

    @Test
    void grassFireDoesNotScreen() {
        NavigationGrid grid = open();
        new FireSmoke(grid).ignited(grid.index(4, 2), FireFuel.GRASS.bit, 1f, 0f, 1f);
        assertFalse(grid.hasTransientOpacity());
    }

    @Test
    void windCarriesSmokeOneCellDownwindAndGivesItBack() {
        NavigationGrid grid = open();
        FireSmoke smoke = new FireSmoke(grid);
        int idx = grid.index(4, 2);
        smoke.ignited(idx, FireFuel.CROWN.bit, 0f, 1f, 0.8f);
        assertTrue(grid.hasTransientOpacity(4, 3));
        smoke.burntOut(idx);
        assertFalse(grid.hasTransientOpacity());
    }
}
