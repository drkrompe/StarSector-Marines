package com.dillon.starsectormarines.battle.deployable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The clock of a mortar section's set-up: the plain window, its extension while engaged, and its cap. */
class MortarSectionWindowTest {

    private static final float DT = 0.5f;

    private static void run(MortarSectionWindow window, float seconds, boolean engaged) {
        for (float t = 0f; t < seconds - 1e-4f; t += DT) window.advance(DT, engaged);
    }

    @Test
    void anUnengagedCrewPacksUpWhenTheWindowRunsOut() {
        MortarSectionWindow window = new MortarSectionWindow(30f, 10f, 90f);
        run(window, 29.5f, false);
        assertFalse(window.expired(), "still inside the window");
        run(window, 0.5f, false);
        assertTrue(window.expired(), "the window is a fixed thirty seconds from activation");
        assertEquals(30f, window.deadline(), 1e-4f, "nothing extended it");
    }

    @Test
    void anEngagedCrewHoldsTheDeadlineAheadOfItself() {
        MortarSectionWindow window = new MortarSectionWindow(30f, 10f, 90f);
        run(window, 29f, true);
        assertFalse(window.expired());
        assertEquals(39f, window.deadline(), 1e-4f, "engaged at 29s holds the deadline ten seconds on");
        run(window, 10f, true);
        assertFalse(window.expired(), "and keeps holding it while it stays engaged");
    }

    @Test
    void aCrewThatFallsSilentRunsOutTheExtensionItHad() {
        MortarSectionWindow window = new MortarSectionWindow(30f, 10f, 90f);
        run(window, 28f, true);
        assertEquals(38f, window.deadline(), 1e-4f);
        run(window, 9.5f, false);
        assertFalse(window.expired(), "the last extension still stands");
        run(window, 0.5f, false);
        assertTrue(window.expired(), "then it packs up");
    }

    @Test
    void engagementCannotHoldTheSectionPastTheCap() {
        MortarSectionWindow window = new MortarSectionWindow(30f, 10f, 90f);
        run(window, 85f, true);
        assertFalse(window.expired());
        assertEquals(90f, window.deadline(), 1e-4f, "extension stops at the cap");
        assertTrue(window.atCap());
        run(window, 5f, true);
        assertTrue(window.expired(), "a crew that is still engaged at the cap packs up all the same");
    }

    @Test
    void anEngagementBeforeTheWindowEndsDoesNotShortenIt() {
        MortarSectionWindow window = new MortarSectionWindow(30f, 10f, 90f);
        run(window, 5f, true);
        assertEquals(30f, window.deadline(), 1e-4f, "engaged at 5s would hold to 15s; the window is the floor");
    }

    @Test
    void zeroExtensionIsAPlainFixedWindow() {
        MortarSectionWindow window = new MortarSectionWindow(15f, 0f, 90f);
        run(window, 14.5f, true);
        assertFalse(window.expired());
        run(window, 0.5f, true);
        assertTrue(window.expired(), "engagement extends nothing when extension is off");
    }

    @Test
    void theCapNeverFallsBelowTheWindow() {
        MortarSectionWindow window = new MortarSectionWindow(30f, 10f, 5f);
        run(window, 29.5f, true);
        assertFalse(window.expired());
        run(window, 0.5f, true);
        assertTrue(window.expired(), "a cap under the window is the window");
    }
}
