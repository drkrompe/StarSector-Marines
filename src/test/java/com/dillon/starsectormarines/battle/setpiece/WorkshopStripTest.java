package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.ServiceLane;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkshopStripTest {

    @Test
    void theStackStandsAgainstTheFarSideOfTheLaneAcrossFromTheDoor() {
        // A lane four wide on rows 8..11 from x = 2 to x = 21, its centre line on row 10; the door opens onto row 8.
        NavigationGrid grid = new NavigationGrid(24, 14);
        for (int x = 2; x <= 21; x++) for (int y = 8; y <= 11; y++) grid.setWalkableFloor(x, y);
        ServiceLane lane = new ServiceLane(1, 10, 22, 10, 2.5f, 10.5f, 21.5f, 10.5f, 4);
        List<float[]> stack = WorkshopStrip.stack(grid, lane, new int[]{8, 8});
        assertEquals(2, stack.size());
        assertArrayEquals(new float[]{8.5f, 11.5f}, stack.get(0));
        assertArrayEquals(new float[]{10.5f, 11.5f}, stack.get(1));
    }
}
