package com.dillon.starsectormarines.battle.decision.goap.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void roomFractionStaysInsideTheRoomsMiddleBand() {
        for (int variant = 1; variant < 500; variant++) {
            float f = ApertureSpread.roomFraction(variant);
            assertTrue(f >= 0.15f && f <= 0.85f, "variant " + variant + " -> " + f);
        }
    }

    @Test
    void consecutiveSquadsAreNotNeighboursInTheRoom() {
        for (int variant = 1; variant < 200; variant++) {
            float gap = Math.abs(ApertureSpread.roomFraction(variant)
                    - ApertureSpread.roomFraction(variant + 1));
            assertTrue(gap > 0.05f, "variant " + variant + " gap " + gap);
        }
    }
}
