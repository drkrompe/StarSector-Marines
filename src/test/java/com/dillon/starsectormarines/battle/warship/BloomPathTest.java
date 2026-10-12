package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The flight of one submunition from the split to its aim point. */
class BloomPathTest {

    private static BloomPath path(long seed, float loop, float weave) {
        return BloomPath.plan(seed, 10f, 20f, 40f, 35f, 1.8f, 1.2f, loop, weave);
    }

    private static float offLine(BloomPath p, float t) {
        return ((p.x(t) - 10f) * 15f - (p.y(t) - 20f) * 30f) / (float) Math.hypot(30f, 15f);
    }

    @Test
    void bothEndsAreExactlyTheCommittedOnes() {
        BloomPath p = path(4L, 0.4f, 0.05f);
        assertEquals(10f, p.x(0f), 1e-4f);
        assertEquals(20f, p.y(0f), 1e-4f);
        assertEquals(40f, p.x(p.totalSeconds()), 1e-3f);
        assertEquals(35f, p.y(p.totalSeconds()), 1e-3f);
        assertEquals(1.8f, p.totalSeconds(), 1e-5f);
    }

    @Test
    void theSameSeedFliesTheSameCurve() {
        BloomPath a = path(4L, 0.4f, 0.05f);
        BloomPath b = path(4L, 0.4f, 0.05f);
        for (float t = 0f; t <= 1.8f; t += 0.2f) {
            assertEquals(a.x(t), b.x(t), 0f);
            assertEquals(a.y(t), b.y(t), 0f);
        }
        assertTrue(a.x(0.9f) != path(5L, 0.4f, 0.05f).x(0.9f));
    }

    @Test
    void withNoLoopAndNoWeaveItIsAStraightLine() {
        BloomPath p = path(4L, 0f, 0f);
        for (float t = 0f; t <= 1.8f; t += 0.3f) {
            assertEquals(0f, offLine(p, t), 1e-3f);
        }
    }

    @Test
    void aLoopLeavesTheLineAndComesBack() {
        BloomPath p = path(4L, 0.45f, 0f);
        float worst = 0f;
        for (float t = 0.2f; t < 1.7f; t += 0.1f) worst = Math.max(worst, Math.abs(offLine(p, t)));
        assertTrue(worst > 2f, "it bulges out of the line: " + worst);
    }

    @Test
    void itMovesAndDivesTowardTheEnd() {
        BloomPath p = path(4L, 0.3f, 0f);
        assertTrue(p.speed(0.05f) > 0f);
        assertTrue(p.speed(1.7f) > p.speed(0.1f), "it dives");
        assertEquals(0f, p.speed(2f), 0f);
    }
}
