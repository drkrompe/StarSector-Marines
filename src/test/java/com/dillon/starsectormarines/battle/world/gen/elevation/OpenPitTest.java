package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenPitTest {

    private static final int W = 80, H = 50;

    private static boolean in(int i, int x0, int y0, int x1, int y1) {
        int x = i % W, y = i / W;
        return x >= x0 && x <= x1 && y >= y0 && y <= y1;
    }

    @Test
    void cutsOnlyFreeGroundInsideItsSite() {
        // A road crosses the site and a building stands in it: neither is free.
        IntPredicate road = i -> i % W >= 38 && i % W <= 41;
        IntPredicate building = i -> in(i, 20, 20, 27, 27);
        IntPredicate free = i -> !road.test(i) && !building.test(i);
        boolean[] pit = OpenPit.plan(W, H, 10, 8, 70, 42, free, i -> true, 3);
        assertNotNull(pit);
        assertTrue(OpenPit.count(pit) >= OpenPit.MIN_CELLS);
        for (int i = 0; i < pit.length; i++) {
            if (!pit[i]) continue;
            assertTrue(free.test(i), "pit over reserved ground at " + i % W + "," + i / W);
            assertTrue(in(i, 10, 8, 70, 42), "pit outside its site at " + i % W + "," + i / W);
        }
    }

    @Test
    void isOnePieceNoNarrowerThanItsOpening() {
        boolean[] pit = OpenPit.plan(W, H, 5, 5, 74, 44, i -> true, i -> true, 11);
        assertNotNull(pit);
        int[] pieces = OpenPit.label(W, H, i -> pit[i]);
        int most = 0;
        for (int v : pieces) most = Math.max(most, v);
        assertEquals(1, most);
        // Opening by the radius leaves every pit cell within a full disc of pit.
        boolean[] core = OpenPit.grow(OpenPit.shrink(pit, W, H, OpenPit.OPEN_RADIUS), W, H, OpenPit.OPEN_RADIUS);
        for (int i = 0; i < pit.length; i++) if (pit[i]) assertTrue(core[i], "sliver at " + i % W + "," + i / W);
    }

    @Test
    void keepsNoPitWhenTooLittleGroundIsFree() {
        IntPredicate free = i -> in(i, 30, 20, 40, 28);
        assertNull(OpenPit.plan(W, H, 10, 8, 70, 42, free, i -> true, 3));
    }

    @Test
    void leavesTheRestOfTheGroundConnected() {
        boolean[] pit = OpenPit.plan(W, H, 10, 8, 70, 42, i -> true, i -> true, 5);
        assertNotNull(pit);
        int[] after = OpenPit.label(W, H, i -> !pit[i]);
        for (int v : after) assertTrue(v <= 1, "the cut split the ground");
    }

    @Test
    void refusesACutThatWouldStrandGroundOutsideItsSite() {
        // A wall runs down the map with one gap, and the site sits on the gap: any pit there cuts the east off.
        IntPredicate walkable = i -> i % W != 50 || (i / W >= 18 && i / W <= 32);
        assertNull(OpenPit.plan(W, H, 30, 4, 66, 46, i -> true, walkable, 5));
    }

    @Test
    void takesInAPocketOfFreeGroundItWouldStrandInsideItsSite() {
        boolean[] cut = new boolean[W * H];
        for (int i = 0; i < cut.length; i++) cut[i] = in(i, 20, 10, 40, 30) && !in(i, 28, 18, 31, 21);
        boolean[] pit = OpenPit.proved(W, H, cut, 15, 5, 45, 35, i -> true, i -> true);
        assertNotNull(pit);
        assertTrue(pit[19 * W + 29], "the island left in the cut is part of the pit");
    }

    @Test
    void refusesToStrandReservedGround() {
        boolean[] cut = new boolean[W * H];
        for (int i = 0; i < cut.length; i++) cut[i] = in(i, 20, 10, 40, 30) && !in(i, 28, 18, 31, 21);
        IntPredicate free = i -> !in(i, 29, 19, 29, 19);
        assertNull(OpenPit.proved(W, H, cut, 15, 5, 45, 35, free, i -> true));
    }

    @Test
    void sendsItsConveyorFromTheFloorOverTheRimToTheWorks() {
        boolean[] pit = OpenPit.plan(W, H, 10, 8, 50, 42, i -> true, i -> true, 5);
        assertNotNull(pit);
        NaturalVoid.Belt belt = OpenPit.belt(W, H, pit, 70f, 25f);
        assertNotNull(belt);
        assertTrue(pit[(int) belt.floorY() * W + (int) belt.floorX()]);
        assertTrue(pit[(int) belt.rimY() * W + (int) belt.rimX()]);
        // A step further along the line toward the works is off the pit: the rim is its last cell there.
        float len = (float) Math.hypot(70f - belt.floorX(), 25f - belt.floorY());
        float bx = belt.rimX() + (70f - belt.floorX()) / len * .5f, by = belt.rimY() + (25f - belt.floorY()) / len * .5f;
        assertFalse(pit[(int) by * W + (int) bx], "the rim is the pit's last cell on the line");
        assertEquals(70f, belt.endX());
        assertEquals(25f, belt.endY());
    }
}
