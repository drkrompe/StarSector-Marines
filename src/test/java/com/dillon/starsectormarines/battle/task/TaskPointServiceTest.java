package com.dillon.starsectormarines.battle.task;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TaskPointServiceTest {

    @Test void specificStationsShareOrdinaryClaimsAndSwitchWithinOneGroup() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        grid.setWalkableFloor(1, 1); grid.setWalkableFloor(4, 1);
        TaskPointService points = new TaskPointService(grid);
        TaskPoint first = new TaskPoint("first", "treat", 1.5f, 1.5f, 1.5f, 2.5f);
        TaskPoint second = new TaskPoint("second", "treat", 4.5f, 1.5f, 4.5f, 2.5f);
        points.register(first); points.register(second);
        assertSame(first, points.claimAt(1, "treat", 1.5f, 1.5f));
        assertSame(second, points.claimNearest(2, "treat", 1.5f, 1.5f));
        assertNull(points.claimAt(1, "treat", 4.5f, 1.5f));
        assertSame(first, points.claimedPoint(1));
        assertNull(points.claimAt(1, "wrong", 4.5f, 1.5f));
        assertNull(points.claimAt(1, "treat", 6.5f, 6.5f));
        points.release(2);
        assertSame(second, points.claimAt(1, "treat", 4.5f, 1.5f));
        assertEquals(0, points.claimant("first"));
        assertEquals(1, points.claimCount());
        assertSame(first, points.claimNearest(2, "treat", 1.5f, 1.5f));
    }

    @Test
    void pointsAreExclusivelyClaimedAndReleasedByActor() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        TaskPointService points = new TaskPointService(grid);
        TaskPoint left = new TaskPoint("bench-left", "bench", 2.5f, 3.5f, 2.5f, 4.5f);
        TaskPoint right = new TaskPoint("bench-right", "bench", 8.5f, 3.5f, 8.5f, 4.5f);
        points.register(left);
        points.register(right);

        assertSame(left, points.claimNearest(11L, "bench", 1f, 3f));
        assertSame(right, points.claimNearest(12L, "bench", 1f, 3f));
        assertNull(points.claimNearest(13L, "bench", 1f, 3f));
        assertEquals(11L, points.claimant("bench-left"));

        TaskPoint terminal = new TaskPoint(
                "terminal", "terminal", 5.5f, 8.5f, 5.5f, 9.5f);
        points.register(terminal);
        assertSame(terminal, points.claimNearest(14L, "terminal", 5f, 8f));
        assertNull(points.claimNearest(11L, "terminal", 5f, 8f));
        assertSame(left, points.claimedPoint(11L),
                "an actor keeps its old station until a replacement is available");

        points.release(11L);

        assertSame(left, points.claimNearest(13L, "bench", 1f, 3f));
        assertEquals(13L, points.claimant("bench-left"));
    }
}
