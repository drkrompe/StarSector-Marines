package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a sheaf of warheads falls. */
class WarheadScatterTest {

    @Test
    void everyWarheadFallsWithinTheSpreadAndTheSeedFixesTheLayout() {
        float[] a = WarheadScatter.points(5L, 7, 100f, 50f, 12f);
        float[] b = WarheadScatter.points(5L, 7, 100f, 50f, 12f);
        assertEquals(14, a.length);
        assertArrayEquals(a, b, 0f);
        for (int i = 0; i < 7; i++) {
            assertTrue(Math.hypot(a[2 * i] - 100f, a[2 * i + 1] - 50f) <= 12.0001f);
        }
        assertFalse(Arrays.equals(a, WarheadScatter.points(6L, 7, 100f, 50f, 12f)));
    }

    @Test
    void theWarheadsCoverTheGroundRatherThanClumping() {
        float[] p = WarheadScatter.points(11L, 7, 0f, 0f, 14f);
        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < 7; i++) {
            for (int j = i + 1; j < 7; j++) {
                nearest = Math.min(nearest, (float) Math.hypot(p[2 * i] - p[2 * j], p[2 * i + 1] - p[2 * j + 1]));
            }
        }
        assertTrue(nearest > 1.5f, "two warheads fell " + nearest + " cells apart");
    }

    @Test
    void theSpreadGrowsWithTheNumberAndSizeOfTheWarheads() {
        assertTrue(WarheadScatter.spreadRadius(7, 3.4f) > WarheadScatter.spreadRadius(3, 3.4f));
        assertTrue(WarheadScatter.spreadRadius(7, 4.5f) > WarheadScatter.spreadRadius(7, 3.4f));
    }
}
