package com.dillon.starsectormarines.battle.story;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColdStartRoadEntryTest {
    @Test void visibilityAloneNeverRefusesAnUnobservedParty() {
        var entry = new ColdStartRoadEntry();
        assertFalse(entry.denied(0, true, false));
        assertFalse(entry.denied(10000, true, false));
        assertFalse(entry.denied(20000, false, true));
    }
    @Test void sustainedPhysicalScreenRefusesEntryAndInterruptionResetsItsClock() {
        var entry = new ColdStartRoadEntry();
        assertFalse(entry.denied(10, true, true));
        assertFalse(entry.denied(249, true, true));
        assertTrue(entry.denied(250, true, true));
        assertFalse(entry.denied(251, false, true));
        assertFalse(entry.denied(252, true, true));
        assertFalse(entry.denied(491, true, true));
        assertTrue(entry.denied(492, true, true));
        assertFalse(entry.denied(493, true, false));
    }
}
