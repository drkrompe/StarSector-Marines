package com.dillon.starsectormarines.battle.decision;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LocalFiringSpreadTest {
    @Test void stampedCountsMatchIndependentPerCellArithmetic() {
        LocalFiringSpread field = new LocalFiringSpread();
        assertTrue(field.reset(0, 0, 47, 31));
        field.beginBuild();
        Random random = new Random(4917);
        float[][] points = new float[150][6];
        for (float[] p : points) {
            // Current snapshot, live current, and live endpoint deliberately differ.
            for (int i = 0; i < p.length; i++) p[i] = random.nextFloat() * 60 - 6;
            field.stampCurrent(p[0], p[1]);
            field.stampDestination(p[4], p[5], p[2], p[3], 1, 1);
        }
        for (int y = 0; y <= 31; y++) for (int x = 0; x <= 47; x++) {
            int expected = 0;
            for (float[] p : points) {
                if (near(p[0], p[1], x, y)) expected++;
                if (x >= 14 && x <= 33 && y >= 14 && y <= 33
                        && near(p[4], p[5], x, y) && !near(p[2], p[3], x, y)) expected++;
            }
            assertEquals(expected, field.countAt(x, y), "cell " + x + "," + y);
        }
    }

    @Test void inclusiveBoundaryUsesSnapshotForFirstPassAndLiveForDedup() {
        LocalFiringSpread field = new LocalFiringSpread();
        assertTrue(field.reset(0, 0, 40, 20));
        field.beginBuild();
        field.stampCurrent(10.5f, 10.5f);
        field.stampDestination(10.5f, 10.5f, 30.5f, 10.5f, 0, 0);
        assertEquals(2, field.countAt(12, 10), "legacy live dedup can retain both contributions");
        assertEquals(0, field.countAt(13, 10));
        field.stampDestination(30.5f, 10.5f, 30.5f, 10.5f, 1, 0);
        assertEquals(0, field.countAt(30, 10), "live-current dedup applies even outside snapshot circle");
        field.stampDestination(30.5f, 10.5f, 2.5f, 2.5f, 0, 0);
        assertEquals(0, field.countAt(30, 10), "a stale distant bucket is not queried by this cell");
    }

    @Test void boundedResetClearsReusedCellsAndRejectsHugeOrEmptyWindows() {
        LocalFiringSpread field = new LocalFiringSpread();
        assertTrue(field.reset(0, 0, 127, 127));
        field.beginBuild();
        field.stampCurrent(1.5f, 1.5f);
        assertEquals(1, field.countAt(1, 1));
        assertTrue(field.reset(1, 1, 2, 2));
        field.beginBuild();
        assertEquals(0, field.countAt(1, 1));
        assertFalse(field.reset(0, 0, 128, 128));
        assertFalse(field.reset(2, 2, 1, 1));
        assertFalse(field.reset(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test void adjacentCellsRespectStaleBucketAndFloatRadiusBoundaries() {
        LocalFiringSpread field = new LocalFiringSpread();
        assertTrue(field.reset(0, 0, 31, 15));
        field.beginBuild();
        field.stampDestination(18.5f, 10.5f, 3.5f, 10.5f, 0, 0);
        assertEquals(1, field.countAt(17, 10));
        assertEquals(0, field.countAt(18, 10));
        field.stampCurrent(Math.nextDown(12.5f), 5.5f);
        field.stampCurrent(12.5f, 5.5f);
        field.stampCurrent(Math.nextUp(12.5f), 5.5f);
        assertEquals(2, field.countAt(10, 5));
    }

    private static boolean near(float px, float py, int x, int y) {
        float dx = px - (x + 0.5f), dy = py - (y + 0.5f);
        return dx * dx + dy * dy <= 4f;
    }
}
