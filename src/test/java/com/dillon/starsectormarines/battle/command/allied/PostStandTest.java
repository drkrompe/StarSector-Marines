package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.ClearanceComponents;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostStandTest {

    private static final VehicleType APC = VehicleType.HEAVY_APC;

    @Test
    void anOpenPostIsItsOwnStand() {
        NavigationGrid grid = open();
        assertArrayEquals(new int[]{20, 20}, choose(grid, 0f, 1f, (x, y) -> false));
    }

    @Test
    void aPostInTheWoodsSlidesAlongTheLineRatherThanBackOrForward() {
        NavigationGrid grid = open();
        wall(grid, 19, 18, 21, 23);

        int[] stand = choose(grid, 0f, 1f, (x, y) -> false);

        assertNotNull(stand);
        assertEquals(20, stand[1], "the line runs across the post, so the hull keeps its depth");
        assertTrue(Math.abs(stand[0] - 20) <= PostStand.REACH);
    }

    @Test
    void aRefusedStandIsNotOfferedAgain() {
        NavigationGrid grid = open();
        wall(grid, 19, 18, 21, 23);
        int[] first = choose(grid, 0f, 1f, (x, y) -> false);

        int[] second = choose(grid, 0f, 1f, (x, y) -> x == first[0] && y == first[1]);

        assertNotNull(second);
        assertTrue(second[0] != first[0] || second[1] != first[1]);
        assertTrue(second[1] <= 20, "never forward of the post");
    }

    @Test
    void groundOnlyAheadOfThePostIsNoStand() {
        NavigationGrid grid = open();
        wall(grid, 14, 10, 26, 20);

        assertNull(choose(grid, 0f, 1f, (x, y) -> false));
    }

    private static int[] choose(NavigationGrid grid, float aheadX, float aheadY,
                                BiPredicate<Integer, Integer> refused) {
        VehicleClearance clearance = VehicleClearance.erode(grid, VehicleClearance.radiusForWidth(APC.visualWidthCells));
        ClearanceComponents components = ClearanceComponents.of(grid, clearance);
        return PostStand.choose(grid, APC, clearance, components, 20, 8, 20, 20, aheadX, aheadY, refused);
    }

    private static NavigationGrid open() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    private static void wall(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) grid.setWalkable(x, y, false);
    }
}
