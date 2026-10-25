package com.dillon.starsectormarines.battle.command.allied;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenedAdvancePaceTest {

    private static final int LATER = ScreenedAdvance.STRAGGLE_TICKS;

    @Test
    void aMemberStuckOffItsSlotFallsOutOfTheBodyUntilItMovesAgain() {
        // The hull of Broad Front's lane 2 (2026-10-24): fifteen cells short of its slot and going nowhere.
        ScreenedAdvance.Pace hull = new ScreenedAdvance.Pace(true, 0);
        hull.observe(0, 198f, 131f, false);
        hull.observe(LATER - 1, 198.4f, 131.2f, false);
        assertTrue(hull.keepsUp(LATER - 1), "still within the grace it is given");
        hull.observe(LATER, 198.4f, 131.2f, false);
        assertFalse(hull.keepsUp(LATER), "it stood off its slot without moving a cell");
        hull.observe(LATER + 1, 200f, 132f, false);
        assertTrue(hull.keepsUp(LATER + 1), "moving again, it is waited for again");
    }

    @Test
    void theSupportTrailsTheMainAlongTheRouteAndBackFromAThreat() {
        AdvanceRoute route = AdvanceRoute.straight(0, 0, 0, 100);
        float[] advancing = ScreenedAdvance.supportPoint(route, 50f, null, 12f);
        float[] main = route.pointAt(50f);
        assertTrue(Math.abs(route.along(advancing[0], advancing[1]) - (route.along(main[0], main[1]) - 12f)) < 1e-3f,
                "twelve cells behind the main along the route");
        float[] atStart = ScreenedAdvance.supportPoint(route, 5f, null, 12f);
        assertTrue(route.along(atStart[0], atStart[1]) >= -1e-3f, "never behind the route's start");
        float[] engaged = ScreenedAdvance.supportPoint(route, 50f, new float[]{1f, 0f}, 12f);
        assertTrue(Math.abs(engaged[0] - (main[0] - 12f)) < 1e-3f && Math.abs(engaged[1] - main[1]) < 1e-3f,
                "straight back from the main, away from a threat on its flank");
    }

    @Test
    void aMemberOnItsSlotKeepsUpStandingStill() {
        ScreenedAdvance.Pace mech = new ScreenedAdvance.Pace(true, 0);
        for (int tick = 0; tick <= 3 * LATER; tick += 15) mech.observe(tick, 203f, 143f, true);
        assertTrue(mech.keepsUp(3 * LATER));
    }

    @Test
    void aLateJoinerIsNotTheBodyUntilItHasWalkedUpToItsSlot() {
        ScreenedAdvance.Pace walker = new ScreenedAdvance.Pace(false, 0);
        walker.observe(0, 213f, 54f, false);
        walker.observe(15, 213f, 56f, false);
        assertFalse(walker.keepsUp(15), "walking up from far behind, it does not hold the formation");
        walker.observe(30, 196f, 154f, true);
        assertTrue(walker.keepsUp(30));
    }
}
