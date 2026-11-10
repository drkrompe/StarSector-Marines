package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WreckTest {

    private static Wreck walker(boolean fromRaise) {
        return new Wreck(1L, Wreck.Kind.WALKER, 0, 10f, 10f, DerelictWalkerFrame.LINE, 5, fromRaise);
    }

    @Test
    void aWreckIsRaisedOnceItsCostIsLaidAndTakesNoMore() {
        Wreck wreck = walker(false);
        assertEquals(0f, wreck.work(Wreck.RAISE_COST - 10f));
        assertTrue(wreck.raisable());
        assertEquals(5f, wreck.work(15f), 1e-4f, "it takes only what it still needs");
        assertEquals(Wreck.State.RAISED, wreck.state());
        assertEquals(7f, wreck.work(7f), "a raised wreck takes nothing");
    }

    @Test
    void aRaisedUnitThatFallsAgainLeavesScrap() {
        assertFalse(walker(true).raisable());
        assertEquals(Wreck.State.SCRAPPED, walker(true).state());
    }

    @Test
    void infantryScrapsAWreckOnlyByStandingOnItUncontestedAtAStretch() {
        Wreck wreck = walker(false);
        assertFalse(wreck.scrap(Wreck.SCRAP_SECONDS - 1f, true, false));
        assertFalse(wreck.scrap(0.5f, true, true), "contested: the count starts again");
        assertEquals(0f, wreck.scrapProgress());
        assertFalse(wreck.scrap(Wreck.SCRAP_SECONDS - 1f, true, false));
        assertTrue(wreck.scrap(1f, true, false));
        assertEquals(Wreck.State.SCRAPPED, wreck.state());
        assertEquals(Wreck.RAISE_COST, wreck.work(Wreck.RAISE_COST), "scrap is never raised");
    }

    @Test
    void aChargeOverAWreckScrapsIt() {
        Wreck wreck = walker(false);
        assertFalse(wreck.blast(30f, 30f));
        assertTrue(wreck.blast(11f, 10f));
        assertFalse(wreck.raisable());
    }
}
