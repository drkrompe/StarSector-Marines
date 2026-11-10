package com.dillon.starsectormarines.battle.world.gen.raid;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tank farm's laws ({@link TankFarm}): tanks in bunds with their fire gaps, a way in, a lane by the road. */
class TankFarmTest {

    private static final int W = 200;

    @Test
    void aBundKeepsItsTanksApartAndOffItsWall() {
        for (int size : new int[]{TankFarm.SMALL, TankFarm.BIG}) {
            TankFarm.Bund bund = TankFarm.bund(10, 20, 10 + TankFarm.bundWidth(size) - 1,
                    20 + TankFarm.bundHeight(size) - 1, size);
            assertEquals(2, bund.tanks().size());
            TankFarm.Tank a = bund.tanks().get(0), b = bund.tanks().get(1);
            assertTrue(b.x() - (a.x() + a.size()) >= TankFarm.FIRE_GAP, "tanks keep their fire gap");
            for (TankFarm.Tank t : bund.tanks()) {
                assertTrue(t.x() - bund.x0() > TankFarm.FIRE_GAP && bund.x1() - (t.x() + t.size() - 1) > TankFarm.FIRE_GAP,
                        "a tank keeps off its bund's wall");
                assertTrue(t.y() - bund.y0() > TankFarm.FIRE_GAP - 1 && bund.y1() - (t.y() + t.size() - 1) > TankFarm.FIRE_GAP - 1,
                        "a tank keeps off its bund's wall");
            }
        }
    }

    /** The wall runs right round the bund but for one stair gap in each long side. */
    @Test
    void aBundsWallHasAWayInOnEachLongSide() {
        TankFarm.Bund bund = TankFarm.bund(0, 0, TankFarm.bundWidth(TankFarm.SMALL) - 1,
                TankFarm.bundHeight(TankFarm.SMALL) - 1, TankFarm.SMALL);
        Set<Long> wall = new HashSet<>();
        for (TankFarm.Wall piece : bund.walls()) {
            assertTrue(wall.add(key(piece.x(), piece.y())), "no two pieces share a cell");
            int x2 = piece.alongX() ? piece.x() + 1 : piece.x(), y2 = piece.alongX() ? piece.y() : piece.y() + 1;
            assertTrue(wall.add(key(x2, y2)), "no two pieces share a cell");
        }
        int open0 = 0, open1 = 0;
        for (int x = bund.x0(); x <= bund.x1(); x++) {
            if (!wall.contains(key(x, bund.y0()))) open0++;
            if (!wall.contains(key(x, bund.y1()))) open1++;
        }
        assertEquals(TankFarm.GAP, open0);
        assertEquals(TankFarm.GAP, open1);
        for (int y = bund.y0(); y <= bund.y1(); y++) {
            assertTrue(wall.contains(key(bund.x0(), y)) && wall.contains(key(bund.x1(), y)), "the ends are closed");
        }
    }

    @Test
    void theLaneRunsAlongTheRoadSideWithTheBayTowardTheTown() {
        // Road below the box (low y), town to the east.
        TankFarm.Plan plan = TankFarm.plan(W, 20, 30, 140, 90, i -> true, 10, 190);
        assertNotNull(plan);
        assertEquals(-1, plan.laneDy());
        assertEquals(30, plan.lane()[1], "the lane is the farm's road-side strip");
        assertEquals(plan.lane()[2], plan.bay()[2], "the bay is at the town's end of the lane");
        for (TankFarm.Bund b : plan.bunds()) {
            assertTrue(b.y0() > plan.lane()[3], "no bund stands in the lane");
            assertTrue(b.x0() >= 20 && b.x1() <= 140 && b.y1() <= 90, "the bunds stay on the farm's ground");
        }
        TankFarm.Plan flipped = TankFarm.plan(W, 20, 30, 140, 90, i -> true, 120, 0);
        assertEquals(1, flipped.laneDy());
        assertEquals(90, flipped.lane()[3]);
        assertEquals(flipped.lane()[0], flipped.bay()[0]);
    }

    @Test
    void bundsKeepAWalkwayBetweenThem() {
        TankFarm.Plan plan = TankFarm.plan(W, 0, 0, 180, 120, i -> true, -10, 0);
        List<TankFarm.Bund> bunds = plan.bunds();
        assertTrue(bunds.size() >= 4);
        assertEquals(TankFarm.BIG, bunds.get(0).tanks().get(0).size(), "a planet's farm stands its big tanks first");
        for (int i = 0; i < bunds.size(); i++) {
            for (int j = i + 1; j < bunds.size(); j++) {
                TankFarm.Bund a = bunds.get(i), b = bunds.get(j);
                int gx = Math.max(b.x0() - a.x1(), a.x0() - b.x1()) - 1;
                int gy = Math.max(b.y0() - a.y1(), a.y0() - b.y1()) - 1;
                assertTrue(Math.max(gx, gy) >= TankFarm.WALKWAY, "bunds " + i + " and " + j + " crowd each other");
            }
        }
    }

    @Test
    void tooLittleGroundHoldsNoFarm() {
        assertNull(TankFarm.plan(W, 0, 0, 20, 12, i -> true, 0, 0));
        assertNull(TankFarm.plan(W, 0, 0, 100, 100, i -> false, 0, 0));
    }

    @Test
    void theFarmStandsOnTheLargestClearGround() {
        // A building occupies the box's west half above row 10: the clear ground is the east and the bottom.
        int[] r = TankFarm.largestClear(W, 0, 0, 59, 39, i -> !(i % W < 30 && i / W >= 10));
        assertArrayEquals(new int[]{30, 0, 59, 39}, r);
        assertFalse(r == null);
    }

    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
}
