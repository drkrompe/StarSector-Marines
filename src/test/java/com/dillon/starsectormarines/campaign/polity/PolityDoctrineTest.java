package com.dillon.starsectormarines.campaign.polity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolityDoctrineTest {

    @Test
    void threePointsAcrossThreeAxesIsTheWholeBudget() {
        assertEquals(3, PolityDoctrine.NONE.pointsRemaining());
        assertEquals(0, PolityDoctrine.of(1, 1, 1).pointsRemaining());
        assertEquals(1, PolityDoctrine.of(2, 0, 0).pointsRemaining());
        assertEquals(0, PolityDoctrine.of(2, 1, 0).pointsRemaining());
    }

    @Test
    void anOverspendOrAnOutOfRangeAxisIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PolityDoctrine.of(2, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> PolityDoctrine.of(1, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> PolityDoctrine.of(3, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PolityDoctrine.of(-1, 0, 0));
    }

    @Test
    void loadingCoercesRatherThanRefusingToOpenTheSave() {
        assertEquals(PolityDoctrine.of(2, 1, 0), PolityDoctrine.clamped(5, 5, 5));
        assertEquals(PolityDoctrine.NONE, PolityDoctrine.clamped(-3, -1, 0));
        assertEquals(PolityDoctrine.of(1, 1, 1), PolityDoctrine.clamped(1, 1, 1));
        assertEquals(PolityDoctrine.of(2, 1, 0), PolityDoctrine.clamped(2, 2, 2));
    }

    @Test
    void numbersMultiplierIsAQuarterPerPoint() {
        assertEquals(1.00f, PolityDoctrine.NONE.numbersMultiplier(), 1e-6f);
        assertEquals(1.25f, PolityDoctrine.of(0, 1, 0).numbersMultiplier(), 1e-6f);
        assertEquals(1.50f, PolityDoctrine.of(0, 2, 0).numbersMultiplier(), 1e-6f);
        assertEquals(1.00f, PolityDoctrine.of(2, 0, 1).numbersMultiplier(), 1e-6f);
    }

    @Test
    void aHeavySupportPointIsWhatAsksForALance() {
        assertFalse(PolityDoctrine.NONE.wantsHeavySupport());
        assertTrue(PolityDoctrine.of(0, 0, 1).wantsHeavySupport());
        assertTrue(PolityDoctrine.of(1, 0, 2).wantsHeavySupport());
    }
}
