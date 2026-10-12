package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PickupLandingSlotsTest {
    private static NavigationGrid floor() {
        var grid = new NavigationGrid(40, 40);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
    @Test void fourBoatsAndHeavyLiftHaveSeparateRampsInsideTheSafeArea() {
        var grid = floor();
        var occupied = new ArrayList<int[]>();
        for (int i = 0; i < 5; i++) {
            int[] pad = PickupLandingSlots.find(grid, 20, 20, occupied, (x, y) -> true);
            assertNotNull(pad);
            assertTrue(Math.hypot(pad[0] - 20, pad[1] - 20) <= PickupLandingSlots.RADIUS);
            for (int[] other : occupied)
                assertTrue(Math.hypot(pad[0] - other[0], pad[1] - other[1]) >= PickupLandingSlots.SEPARATION);
            occupied.add(pad);
        }
        assertArrayEquals(new int[]{20, 20}, occupied.get(0));
    }
    @Test void buildingsAndUnreachableGroundCannotBecomeRamps() {
        var grid = floor();
        assertNull(PickupLandingSlots.find(grid, 20, 20, List.of(), (x, y) -> false));
        var blocked = new NavigationGrid(40, 40);
        blocked.setWalkableFloor(20, 20);
        for (int y = 25; y < 31; y++) for (int x = 20; x < 26; x++) blocked.setWalkableFloor(x, y);
        assertNull(PickupLandingSlots.find(blocked, 20, 20, List.of(), (x, y) -> true));
    }
}
