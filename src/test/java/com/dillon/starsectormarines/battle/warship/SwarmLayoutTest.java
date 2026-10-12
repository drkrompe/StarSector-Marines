package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a swarm comes down and when. */
class SwarmLayoutTest {

    @Test
    void theSameSeedLaysOutTheSameSwarmAndAnotherSeedDoesNot() {
        SwarmLayout.Sheaf a = SwarmLayout.sheaf(9L, 12, 100f, 40f, 10f, 20f, 40f, 0.4f, 0.6f, 2.5f);
        SwarmLayout.Sheaf b = SwarmLayout.sheaf(9L, 12, 100f, 40f, 10f, 20f, 40f, 0.4f, 0.6f, 2.5f);
        assertArrayEquals(a.aimX(), b.aimX(), 0f);
        assertArrayEquals(a.aimY(), b.aimY(), 0f);
        assertArrayEquals(a.arrivalSeconds(), b.arrivalSeconds(), 0f);
        assertFalse(Arrays.equals(a.aimX(),
                SwarmLayout.sheaf(10L, 12, 100f, 40f, 10f, 20f, 40f, 0.4f, 0.6f, 2.5f).aimX()));
    }

    @Test
    void everyBodyAimsInsideTheDisc() {
        SwarmLayout.Sheaf s = SwarmLayout.sheaf(3L, 32, 60f, 30f, 12f, 0f, 30f, 0.7f, 0.6f, 2.5f);
        for (int i = 0; i < s.size(); i++) {
            assertTrue(Math.hypot(s.aimX()[i] - 60f, s.aimY()[i] - 30f) <= 12.0001f);
        }
    }

    @Test
    void arrivalsRollInOrderAcrossTheWholeWindow() {
        SwarmLayout.Sheaf s = SwarmLayout.sheaf(5L, 20, 0f, 0f, 9f, -30f, 0f, 0.4f, 0.6f, 2.5f);
        float[] t = s.arrivalSeconds();
        for (int i = 1; i < t.length; i++) assertTrue(t[i] >= t[i - 1], "arrivals are in order");
        assertTrue(t[0] >= 0.6f - 1e-4f && t[t.length - 1] <= 2.5f + 1e-4f, "inside the window");
        assertTrue(t[t.length - 1] - t[0] > 1.5f, "a drumroll, not a single thump");
    }

    @Test
    void theWindowIsAParameter() {
        float[] narrow = SwarmLayout.sheaf(5L, 20, 0f, 0f, 9f, -30f, 0f, 0.4f, 0.15f, 0.8f).arrivalSeconds();
        float[] wide = SwarmLayout.sheaf(5L, 20, 0f, 0f, 9f, -30f, 0f, 0.4f, 1.2f, 4f).arrivalSeconds();
        assertTrue(narrow[narrow.length - 1] <= 0.8f + 1e-4f);
        assertTrue(wide[wide.length - 1] > 3f && wide[0] >= 1.2f - 1e-4f);
    }

    @Test
    void fullRippleLandsTheNearestFirstAndNoRippleDoesNot() {
        SwarmLayout.Sheaf ordered = SwarmLayout.sheaf(7L, 24, 80f, 0f, 14f, 0f, 0f, 1f, 0f, 3f);
        double last = -1;
        for (int i = 0; i < ordered.size(); i++) {
            double d = Math.hypot(ordered.aimX()[i], ordered.aimY()[i]);
            assertTrue(d >= last - 1e-3, "nearest first");
            last = d;
        }
        SwarmLayout.Sheaf shuffled = SwarmLayout.sheaf(7L, 24, 80f, 0f, 14f, 0f, 0f, 0f, 0f, 3f);
        boolean inversion = false;
        for (int i = 1; i < shuffled.size(); i++) {
            double now = Math.hypot(shuffled.aimX()[i], shuffled.aimY()[i]);
            double before = Math.hypot(shuffled.aimX()[i - 1], shuffled.aimY()[i - 1]);
            if (now < before) inversion = true;
        }
        assertTrue(inversion, "with no ripple the order is chance");
    }

    @Test
    void theScatterCoversTheDiscRatherThanClumping() {
        SwarmLayout.Sheaf s = SwarmLayout.sheaf(11L, 30, 0f, 0f, 14f, -40f, 0f, 0.1f, 0f, 2f);
        float nearest = Float.MAX_VALUE;
        float widest = 0f;
        for (int i = 0; i < s.size(); i++) {
            for (int j = i + 1; j < s.size(); j++) {
                float d = (float) Math.hypot(s.aimX()[i] - s.aimX()[j], s.aimY()[i] - s.aimY()[j]);
                nearest = Math.min(nearest, d);
                widest = Math.max(widest, d);
            }
        }
        assertTrue(widest > 20f, "the swarm spans the disc: " + widest);
        assertTrue(nearest > 0.05f, "no two bodies on one point");
    }
}
