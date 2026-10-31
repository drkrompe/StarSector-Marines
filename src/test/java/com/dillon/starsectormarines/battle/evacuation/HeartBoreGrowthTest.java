package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.power.WalkingBarrage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartBoreGrowthTest {

    private static final int W = 120, H = 80;
    private static final float FROM = 0.3f;
    private static final HeartBoreGrowth.Situation EMPTY = new HeartBoreGrowth.Situation(List.of(), List.of(), new float[0]);

    /** A hand-made approach along x: open everywhere but {@code blocked}, scorched where {@code scorched} says. */
    private record Field(boolean[] blocked, boolean[] scorched) implements HeartBoreGrowth.Ground {
        Field() { this(new boolean[W * H], new boolean[W * H]); }
        @Override public int width() { return W; }
        @Override public int height() { return H; }
        @Override public float depth(int x, int y) { return x / (float) (W - 1); }
        @Override public float across(int x, int y) { return y / (float) (H - 1); }
        @Override public boolean standable(int x, int y) { return !blocked[y * W + x]; }
        @Override public boolean scorched(int x, int y) { return scorched[y * W + x]; }
    }

    @Test
    void aGrownBoreStandsOnItsFlankPastTheRideAndShortOfTheHeart() {
        Field field = new Field();
        for (long seed = 0; seed < 40; seed++) {
            for (boolean north : new boolean[]{true, false}) {
                int[] cell = HeartBoreGrowth.site(field, EMPTY, north, FROM, new SplittableRandom(seed));
                assertNotNull(cell);
                float depth = field.depth(cell[0], cell[1]), across = field.across(cell[0], cell[1]);
                assertTrue(depth >= FROM && depth <= HeartBoreGrowth.DEPTH_TO, "depth " + depth);
                assertTrue(north ? across > 0.5f : across < 0.5f, "on its own flank: " + across);
            }
        }
    }

    @Test
    void itNeverGrowsOnOrBesideBurntGround() {
        Field field = new Field();
        // The whole north half has burnt: the north flank has nowhere; the south still does.
        for (int y = H / 2 - HeartBoreGrowth.SCORCH_REACH; y < H; y++) {
            for (int x = 0; x < W; x++) field.scorched()[y * W + x] = true;
        }
        assertNull(HeartBoreGrowth.site(field, EMPTY, true, FROM, new SplittableRandom(7)));
        assertNotNull(HeartBoreGrowth.site(field, EMPTY, false, FROM, new SplittableRandom(7)));
        // One burnt cell: no site lands within the scorch reach of it.
        Field spotted = new Field();
        spotted.scorched()[60 * W + 60] = true;
        for (long seed = 0; seed < 40; seed++) {
            int[] cell = HeartBoreGrowth.site(spotted, EMPTY, true, FROM, new SplittableRandom(seed));
            assertNotNull(cell);
            assertFalse(Math.abs(cell[0] - 60) <= HeartBoreGrowth.SCORCH_REACH
                    && Math.abs(cell[1] - 60) <= HeartBoreGrowth.SCORCH_REACH, "beside the burn: " + cell[0] + "," + cell[1]);
        }
    }

    @Test
    void itKeepsClearOfFriendsBoresTheBarrageAndUnreachableGround() {
        Field field = new Field();
        // Only a small patch of the north flank is reachable, clear ground.
        for (int i = 0; i < W * H; i++) field.blocked()[i] = true;
        for (int y = 58; y <= 62; y++) for (int x = 70; x <= 74; x++) field.blocked()[y * W + x] = false;
        int[] cell = HeartBoreGrowth.site(field, EMPTY, true, FROM, new SplittableRandom(3));
        assertNotNull(cell);
        assertTrue(cell[0] >= 70 && cell[0] <= 74 && cell[1] >= 58 && cell[1] <= 62);

        HeartBoreGrowth.Situation friend = new HeartBoreGrowth.Situation(List.<float[]>of(new float[]{72f, 60f + 20f}),
                List.of(), new float[0]);
        assertNull(HeartBoreGrowth.site(field, friend, true, FROM, new SplittableRandom(3)), "a friend in reach");
        HeartBoreGrowth.Situation bore = new HeartBoreGrowth.Situation(List.of(),
                List.<float[]>of(new float[]{72f + 20f, 60f}), new float[0]);
        assertNull(HeartBoreGrowth.site(field, bore, true, FROM, new SplittableRandom(3)), "another bore too near");
        HeartBoreGrowth.Situation barrage = new HeartBoreGrowth.Situation(List.of(), List.of(),
                new float[]{72f / (W - 1)});
        assertNull(HeartBoreGrowth.site(field, barrage, true, FROM, new SplittableRandom(3)), "on the barrage's line");
    }

    @Test
    void sameSeedSameSite() {
        Field field = new Field();
        assertArrayEquals(HeartBoreGrowth.site(field, EMPTY, true, FROM, new SplittableRandom(11)),
                HeartBoreGrowth.site(field, EMPTY, true, FROM, new SplittableRandom(11)));
    }

    @Test
    void theClockWaitsForItsTimeAndForRoom() {
        assertFalse(HeartBoreGrowth.due(99, 100, 0));
        assertTrue(HeartBoreGrowth.due(100, 100, 0));
        assertTrue(HeartBoreGrowth.due(500, 100, HeartBoreGrowth.MAX_LIVING - 1));
        assertFalse(HeartBoreGrowth.due(500, 100, HeartBoreGrowth.MAX_LIVING), "the cap is full");
    }

    @Test
    void theBarrageBandIsTheFiringLineAndTheNext() {
        WalkingBarrage.Plan plan = new WalkingBarrage.Plan(List.of(
                new WalkingBarrage.Line(0.3f, 10f, 10f, 0),
                new WalkingBarrage.Line(0.4f, 20f, 10f, 0),
                new WalkingBarrage.Line(0.5f, 30f, 10f, 0)),
                new WalkingBarrage.Shell(3f, 70f, 20f, 60, 3f), 2.5f, 0.012f);
        assertArrayEquals(new float[]{0.3f}, HeartBoreGrowth.barrageDepths(plan, 0f), "before it opens: the first line");
        assertArrayEquals(new float[]{0.4f, 0.5f}, HeartBoreGrowth.barrageDepths(plan, 25f));
        assertEquals(0, HeartBoreGrowth.barrageDepths(plan, 45f).length, "the plan is done");
        assertEquals(0, HeartBoreGrowth.barrageDepths(null, 5f).length);
    }
}
