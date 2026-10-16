package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipSpacingTest {

    @Test
    void twoHullsSideBySideAreTheirBeamsApart() {
        // Both noses north (facing 0: nose along (0, 1)); 20 long, 10 wide; centres 16 apart across the beam.
        float g = WarshipSpacing.gap(0f, 0f, 0f, 20f, 10f, 16f, 0f, 0f, 20f, 10f);
        assertEquals(6f, g, 1e-4f);
    }

    @Test
    void twoHullsInLineAheadAreTheirLengthsApart() {
        float g = WarshipSpacing.gap(0f, 0f, 0f, 20f, 10f, 0f, 30f, 0f, 20f, 10f);
        assertEquals(10f, g, 1e-4f);
    }

    @Test
    void overlapIsNegativeByTheShallowestDepth() {
        float g = WarshipSpacing.gap(0f, 0f, 0f, 20f, 10f, 8f, 0f, 0f, 20f, 10f);
        assertEquals(-2f, g, 1e-4f);
    }

    @Test
    void aHullTurnedAcrossAnotherIsJudgedOnItsOwnAxes() {
        // The second hull lies east-west (facing 90), so its 20-cell length lies along x.
        float g = WarshipSpacing.gap(0f, 0f, 0f, 20f, 10f, 20f, 0f, 90f, 20f, 10f);
        assertEquals(5f, g, 1e-4f);
        assertTrue(WarshipSpacing.gap(0f, 0f, 0f, 20f, 10f, 14f, 0f, 90f, 20f, 10f) < 0f);
    }
}
