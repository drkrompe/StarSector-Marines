package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignClockTest {

    @Test
    void counterContinuesTheScaleItsAnchorWasStampedIn() {
        // A legacy save was already numbering days from the calendar-day value it had.
        assertEquals(12f, CampaignClock.dayFrom(12, 0f));
        assertEquals(19f, CampaignClock.dayFrom(12, 7f));
        // A new game anchors at zero.
        assertEquals(0f, CampaignClock.dayFrom(0, 0f));
        assertEquals(45f, CampaignClock.dayFrom(0, 45f));
    }

    @Test
    void counterCrossesMonthBoundariesInsteadOfWrapping() {
        // The bug this replaces: a 7-day window armed on calendar day 27 was never
        // reached, because getDay() wrapped to 1 before it got there.
        int armed = (int) CampaignClock.dayFrom(27, 0f);
        int deadline = armed + 7;
        assertEquals(34, deadline);
        assertTrue(CampaignClock.dayFrom(27, 7f) >= deadline);
    }

    @Test
    void counterNeverWalksBackwards() {
        // A rewound or re-anchored clock must not re-fire every elapsed-days check.
        assertEquals(40f, CampaignClock.dayFrom(40, -5f));
        assertEquals(40f, CampaignClock.dayFrom(40, 0f));
    }

    @Test
    void counterIsMonotonicAcrossAYearOfElapsedDays() {
        float previous = Float.NEGATIVE_INFINITY;
        for (int elapsed = 0; elapsed <= 365; elapsed++) {
            float day = CampaignClock.dayFrom(12, elapsed);
            assertTrue(day >= previous, "regressed at elapsed=" + elapsed);
            previous = day;
        }
        assertEquals(377f, CampaignClock.dayFrom(12, 365f));
    }
}
