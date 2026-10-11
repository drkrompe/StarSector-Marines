package com.dillon.starsectormarines.battle.decision.goap.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApertureSpreadTest {

    @Test
    void legacyVariantStartsWhereTheSearchAlwaysDid() {
        assertEquals(0, ApertureSpread.startIndex(ApertureSpread.NO_VARIANT, 5));
    }

    @Test
    void squadsRotateAcrossThePortalsOfAZone() {
        assertEquals(0, ApertureSpread.startIndex(1, 3));
        assertEquals(1, ApertureSpread.startIndex(2, 3));
        assertEquals(2, ApertureSpread.startIndex(3, 3));
        assertEquals(0, ApertureSpread.startIndex(4, 3));
    }

    @Test
    void aZoneWithNoPortalsStartsAtZero() {
        assertEquals(0, ApertureSpread.startIndex(7, 0));
    }
}
