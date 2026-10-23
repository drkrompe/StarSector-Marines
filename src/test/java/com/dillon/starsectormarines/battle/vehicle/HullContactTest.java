package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Facing -90 puts a nose on +x, facing 90 on -x. Hulls 4 long and 2 wide. */
class HullContactTest {
    private static final float L = 2f, W = 1f;
    private final float[] out = new float[3];

    @Test void hullsNoseToNoseOverlapAlongTheirLength() {
        assertTrue(HullContact.overlap(0, 0, -90f, L, W, 3.5f, 0, 90f, L, W, 0f, out));
        assertEquals(1f, out[0], 1e-5f);
        assertEquals(.5f, out[2], 1e-5f);
    }

    @Test void theSkinReachesPastTheFootprint() {
        assertFalse(HullContact.overlap(0, 0, -90f, L, W, 4.2f, 0, 90f, L, W, 0f, out));
        assertTrue(HullContact.overlap(0, 0, -90f, L, W, 4.2f, 0, 90f, L, W, HullContact.SKIN, out));
        assertTrue(out[2] - HullContact.SKIN < 0f, "near, not touching");
    }

    @Test void sideBySideHullsAreApartAndARotatedOneIsTested() {
        assertFalse(HullContact.overlap(0, 0, -90f, L, W, 0, 2.1f, -90f, L, W, 0f, out));
        // Turned across the first one's bow, its long side across the road: it touches the nose.
        assertTrue(HullContact.overlap(0, 0, -90f, L, W, 2.9f, 0, 0f, L, W, 0f, out));
        assertEquals(1f, out[0], 1e-5f);
    }

    @Test void aFollowerTakesThePaceOfTheHullAhead() {
        assertEquals(1f, HullContact.yieldSpeed(2.5f, 1f, 1f), 1e-6f);
        assertEquals(0f, HullContact.yieldSpeed(2.5f, 1f, -1f), "never pushed through the one ahead");
        assertEquals(2.5f, HullContact.yieldSpeed(2.5f, .1f, 0f), "going past it, not into it");
        assertEquals(2.5f, HullContact.yieldSpeed(2.5f, -1f, 0f), "driving away from it");
        assertEquals(-1f, HullContact.yieldSpeed(-2f, -1f, 1f), 1e-6f, "reversing into it");
    }

    @Test void theLighterGivesWayAndEqualsBreakTheTieByIdOnBothSides() {
        assertTrue(HullContact.yields(2.5f, 1L, 12f, 9L));
        assertFalse(HullContact.yields(12f, 9L, 2.5f, 1L));
        assertNotEquals(HullContact.yields(12f, 3L, 12f, 4L), HullContact.yields(12f, 4L, 12f, 3L));
    }

    @Test void headOnHullsAreSteeredToOppositeSides() {
        float[] side = new float[2];
        // Dead in line: b goes to a's left, which is b's own right.
        HullContact.lateral(0, 0, -90f, 3.5f, 0, 1f, 0f, side);
        assertEquals(1f, side[1], 1e-5f, "a's left with its nose on +x is +y");
        // Already offset to a's right: b keeps going that way.
        HullContact.lateral(0, 0, -90f, 3.5f, -.6f, 1f, 0f, side);
        assertEquals(-1f, side[1], 1e-5f);
    }
}
